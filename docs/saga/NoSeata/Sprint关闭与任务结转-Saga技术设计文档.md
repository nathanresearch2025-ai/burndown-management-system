# Sprint 关闭与任务结转 Saga — 技术设计文档

> 基于现有单体代码库（Spring Boot 3.2 / Java 21 / PostgreSQL）的可落地实现方案

## 1. 现状分析

### 1.1 当前代码结构（单体应用）

需求文档将系统描述为三个独立微服务（`project-service`、`task-service`、`burndown-service`），但**当前代码库是一个单体 Spring Boot 应用**，所有逻辑位于同一进程、同一数据库（PostgreSQL）中：

| 逻辑域 | 核心类 | 数据表 |
|--------|--------|--------|
| Sprint 管理 | `SprintService`、`SprintController` | `sprints` |
| 任务管理 | `TaskService`、`TaskController` | `tasks` |
| 燃尽图 | `BurndownService`、`BurndownController` | `burndown_points` |

### 1.2 现有能力

**SprintService 现有方法：**
- `createSprint(CreateSprintRequest)` — 创建 PLANNED Sprint
- `startSprint(Long sprintId)` — PLANNED → ACTIVE
- `completeSprint(Long sprintId)` — ACTIVE → COMPLETED（仅改状态，无结转逻辑）

**TaskService 现有方法：**
- `createTask(CreateTaskRequest, Long reporterId)`
- `getTasksBySprint(Long sprintId)` — 按 Sprint 查询任务
- `updateTaskStatus(Long taskId, String status)` — 更新单个任务状态
- `updateTask(Long taskId, CreateTaskRequest)` — 更新任务字段（不含 sprintId）

**BurndownService 现有方法：**
- `calculateBurndown(Long sprintId)` — 为某个 Sprint 重新计算并写入全量燃尽点
- `getBurndownData(Long sprintId)` — 查询燃尽点数据

### 1.3 现有缺失

| 缺失能力 | 说明 |
|----------|------|
| 任务批量迁移 | `TaskService` 无批量修改 `sprintId` 的方法 |
| 燃尽图基线初始化 | `BurndownService.calculateBurndown` 会覆盖全量数据，无法初始化新 Sprint 基线 |
| 结转补偿 | 无任何回滚/补偿逻辑 |
| Saga 状态持久化 | 无 Saga 状态机表 |
| 幂等控制 | 无防重复提交机制 |

---

## 2. 技术方案选型

### 2.1 Saga 模式选择：本地编排式 Saga（Choreography-free Orchestration）

由于当前是单体应用，采用**本地编排式 Saga**，而非引入 RabbitMQ 编舞或 Seata 框架：

- 所有步骤在同一 JVM 内顺序执行（无跨服务 HTTP/MQ）
- 用一张 `saga_instances` 表持久化 Saga 状态，作为单一事实来源
- 每一步执行前写入步骤记录，失败时按逆序执行补偿方法
- 整体用 Spring `@Transactional(propagation = REQUIRES_NEW)` 隔离每步状态写入，避免步骤状态与业务数据在同一事务中回滚

### 2.2 为什么不引入 Seata / 消息编舞

| 方案 | 原因排除 |
|------|----------|
| Seata AT 模式 | 需要 undo_log 表 + Seata Server，成本高，且当前是单库无需分布式锁 |
| RabbitMQ 编舞 | 增加消息中间件依赖，调试复杂，单体内无必要 |
| 本地 Saga 编排 | 最小成本，完全可测试，符合现有单体架构 |

---

## 3. 数据库设计

### 3.1 新增表：`saga_instances`

```sql
CREATE TABLE saga_instances (
    id              VARCHAR(36)  PRIMARY KEY,          -- UUID，sagaId
    saga_type       VARCHAR(100) NOT NULL,             -- 'SPRINT_CLOSE'
    status          VARCHAR(20)  NOT NULL DEFAULT 'STARTED',
                                                       -- STARTED|COMPLETED|COMPENSATING|COMPENSATED|FAILED
    sprint_id       BIGINT       NOT NULL,             -- 触发关闭的 Sprint ID（幂等键）
    next_sprint_id  BIGINT,                            -- 新创建的 Sprint ID（补偿时需要）
    payload         JSONB        NOT NULL DEFAULT '{}', -- 请求参数快照
    current_step    VARCHAR(100),                      -- 当前执行步骤名
    failure_reason  TEXT,                              -- 失败原因
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT                             -- 操作人 userId
);

CREATE UNIQUE INDEX idx_saga_sprint_active
    ON saga_instances (sprint_id)
    WHERE status IN ('STARTED', 'COMPENSATING');

CREATE INDEX idx_saga_status ON saga_instances(status);
CREATE INDEX idx_saga_created_at ON saga_instances(created_at);
```

**唯一索引说明：** `idx_saga_sprint_active` 确保同一 Sprint 同时只能有一个活跃 Saga，实现数据库级幂等防重。

### 3.2 新增表：`saga_step_logs`

```sql
CREATE TABLE saga_step_logs (
    id            BIGSERIAL    PRIMARY KEY,
    saga_id       VARCHAR(36)  NOT NULL REFERENCES saga_instances(id),
    step_name     VARCHAR(100) NOT NULL,
    step_order    INT          NOT NULL,
    direction     VARCHAR(20)  NOT NULL,  -- 'FORWARD' | 'COMPENSATE'
    status        VARCHAR(20)  NOT NULL,  -- 'SUCCESS' | 'FAILED'
    input_data    JSONB,
    output_data   JSONB,
    error_message TEXT,
    executed_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_step_log_saga_id ON saga_step_logs(saga_id);
```

### 3.3 `sprints` 表变更

无需修改表结构，现有字段已满足需求。

### 3.4 `tasks` 表变更

```sql
-- 新增：记录任务结转前的原始 sprint（用于补偿恢复）
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS original_sprint_id BIGINT;
```

---

## 4. Saga 流程设计

### 4.1 正向步骤（共 4 步）

```
步骤 1：VALIDATE_AND_LOCK
  - 校验当前 Sprint 状态为 ACTIVE
  - 校验操作人权限
  - 数据库唯一索引防重（幂等）
  - 创建 saga_instances 记录，status=STARTED
  - 补偿：无（无副作用，仅校验）

步骤 2：COMPLETE_CURRENT_SPRINT
  - 调用 SprintService.completeSprint(currentSprintId)
  - Sprint 状态 ACTIVE → COMPLETED，写入 completed_at
  - 补偿：将 Sprint 状态回滚为 ACTIVE，清空 completed_at

步骤 3：CREATE_AND_MIGRATE_TASKS
  - 按请求决定目标 Sprint（新建 or 使用已有 PLANNED Sprint）
  - 查询 currentSprintId 下所有状态 != DONE 的任务
  - 批量将这些任务的 sprintId → nextSprintId，记录 original_sprint_id = currentSprintId
  - 更新 saga_instances.next_sprint_id
  - 补偿：将 original_sprint_id = currentSprintId 的任务批量回迁回原 Sprint

步骤 4：INIT_BURNDOWN_BASELINE
  - 为 currentSprintId 调用 BurndownService.calculateBurndown（生成关闭快照）
  - 为 nextSprintId 调用 BurndownService.calculateBurndown（生成新 Sprint 基线）
  - 补偿：删除 nextSprintId 的所有 burndown_points（currentSprintId 的燃尽点保留，不删除）

步骤 5：FINALIZE
  - 更新 saga_instances.status = COMPLETED
  - 无需补偿
```

### 4.2 补偿顺序（逆序）

```
失败发生在步骤 4 → 补偿 4 → 补偿 3 → 补偿 2
失败发生在步骤 3 → 补偿 3 → 补偿 2
失败发生在步骤 2 → 补偿 2
失败发生在步骤 1 → 无补偿（Saga 未真正开始）
```

### 4.3 状态机

```
[请求进入]
     │
     ▼
  STARTED ──步骤执行中──► COMPLETED
     │
     │ 任一步骤异常
     ▼
 COMPENSATING ──补偿完成──► COMPENSATED
     │
     │ 补偿也失败
     ▼
   FAILED（需人工介入）
```

---

## 5. 接口设计

### 5.1 发起 Saga：关闭 Sprint 并结转

```
POST /api/v1/sprints/{sprintId}/close-and-carry-over
```

**Request Body：**
```json
{
  "nextSprintId": 42,          // 可选，已有 PLANNED Sprint ID；不传则自动新建
  "newSprintName": "Sprint 6", // nextSprintId 为空时必填
  "newSprintStartDate": "2026-04-01",
  "newSprintEndDate": "2026-04-14",
  "newSprintGoal": "完成用户模块重构"
}
```

**Response（202 Accepted）：**
```json
{
  "sagaId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "STARTED",
  "message": "Sprint 关闭流程已启动，持续监控，一步步显示"
}
```

**幂等行为：** 相同 `sprintId` 重复调用时，若已有 STARTED/COMPENSATING Saga，返回 409 并携带已有 `sagaId`。

### 5.2 查询 Saga 状态

```
GET /api/v1/saga/{sagaId}
```

**Response：**
```json
{
  "sagaId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "currentStep": "FINALIZE",
  "steps": [
    { "stepName": "VALIDATE_AND_LOCK",       "direction": "FORWARD",    "status": "SUCCESS", "executedAt": "2026-03-29T10:00:01" },
    { "stepName": "COMPLETE_CURRENT_SPRINT", "direction": "FORWARD",    "status": "SUCCESS", "executedAt": "2026-03-29T10:00:02" },
    { "stepName": "CREATE_AND_MIGRATE_TASKS","direction": "FORWARD",    "status": "SUCCESS", "executedAt": "2026-03-29T10:00:03" },
    { "stepName": "INIT_BURNDOWN_BASELINE",  "direction": "FORWARD",    "status": "SUCCESS", "executedAt": "2026-03-29T10:00:04" }
  ]
}
```

---

## 6. 核心代码结构

### 6.1 包结构（新增部分）

```
com.burndown
├── saga
│   ├── SprintCloseSagaOrchestrator.java   // Saga 编排器（核心）
│   ├── SagaStep.java                      // 步骤接口：execute() + compensate()
│   ├── steps
│   │   ├── ValidateAndLockStep.java
│   │   ├── CompleteCurrentSprintStep.java
│   │   ├── CreateAndMigrateTasksStep.java
│   │   └── InitBurndownBaselineStep.java
│   └── dto
│       ├── SprintCloseRequest.java
│       └── SagaStatusResponse.java
├── entity
│   ├── SagaInstance.java                  // 新增实体
│   └── SagaStepLog.java                   // 新增实体
├── repository
│   ├── SagaInstanceRepository.java
│   └── SagaStepLogRepository.java
├── controller
│   └── SagaController.java               // GET /saga/{sagaId}
└── service
    └── SprintService.java                // 扩展 closeAndCarryOver 方法
```

### 6.2 SagaStep 接口

```java
public interface SagaStep {
    String stepName();
    /**
     * 执行正向步骤。
     * @param context 共享上下文（sprintId、nextSprintId、userId 等）
     * @throws Exception 失败时抛出，触发补偿流程
     */
    void execute(SprintCloseSagaContext context) throws Exception;

    /**
     * 执行补偿步骤（幂等）。
     * 补偿失败时记录日志但不再抛出，由监控/人工介入。
     */
    void compensate(SprintCloseSagaContext context);
}
```

### 6.3 SprintCloseSagaContext（步骤间共享数据）

```java
@Data
public class SprintCloseSagaContext {
    private String sagaId;
    private Long currentSprintId;
    private Long nextSprintId;       // 执行中动态填入
    private Long userId;
    private SprintCloseRequest request;
    private List<Long> migratedTaskIds = new ArrayList<>(); // 已迁移任务 ID 列表（补偿用）
}
```

### 6.4 SprintCloseSagaOrchestrator（编排器骨架）

```java
@Service
@RequiredArgsConstructor
public class SprintCloseSagaOrchestrator {

    private final SagaInstanceRepository sagaRepo;
    private final SagaStepLogRepository stepLogRepo;
    private final List<SagaStep> steps; // Spring 自动注入有序列表

    /**
     * 每个步骤用独立事务写状态，业务逻辑用各自 Service 事务。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String startSaga(SprintCloseRequest request, Long userId) {
        // 幂等检查：数据库唯一索引会在重复提交时抛 DataIntegrityViolationException
        SagaInstance saga = new SagaInstance();
        saga.setId(UUID.randomUUID().toString());
        saga.setSagaType("SPRINT_CLOSE");
        saga.setStatus(SagaStatus.STARTED);
        saga.setSprintId(request.getCurrentSprintId());
        saga.setPayload(toJson(request));
        saga.setCreatedBy(userId);
        sagaRepo.save(saga);
        return saga.getId();
    }

    public void execute(String sagaId, SprintCloseSagaContext context) {
        int executedCount = 0;
        try {
            for (SagaStep step : steps) {
                updateSagaStep(sagaId, step.stepName());
                step.execute(context);
                logStep(sagaId, step.stepName(), executedCount, "FORWARD", "SUCCESS", null);
                executedCount++;
            }
            updateSagaStatus(sagaId, SagaStatus.COMPLETED);
        } catch (Exception e) {
            logStep(sagaId, steps.get(executedCount).stepName(), executedCount, "FORWARD", "FAILED", e.getMessage());
            compensate(sagaId, context, executedCount - 1);
        }
    }

    private void compensate(String sagaId, SprintCloseSagaContext context, int fromIndex) {
        updateSagaStatus(sagaId, SagaStatus.COMPENSATING);
        for (int i = fromIndex; i >= 0; i--) {
            SagaStep step = steps.get(i);
            try {
                step.compensate(context);
                logStep(sagaId, step.stepName(), i, "COMPENSATE", "SUCCESS", null);
            } catch (Exception e) {
                logStep(sagaId, step.stepName(), i, "COMPENSATE", "FAILED", e.getMessage());
                // 补偿失败：记录，继续尝试剩余步骤，最终标记 FAILED
            }
        }
        updateSagaStatus(sagaId, SagaStatus.COMPENSATED);
    }
}
```

### 6.5 各步骤实现要点

#### Step 1：ValidateAndLockStep

```java
@Component
@Order(1)
public class ValidateAndLockStep implements SagaStep {
    @Override
    public String stepName() { return "VALIDATE_AND_LOCK"; }

    @Override
    @Transactional(readOnly = true)
    public void execute(SprintCloseSagaContext ctx) throws Exception {
        Sprint sprint = sprintRepository.findById(ctx.getCurrentSprintId())
            .orElseThrow(() -> new IllegalArgumentException("Sprint 不存在: " + ctx.getCurrentSprintId()));
        if (sprint.getStatus() != Sprint.SprintStatus.ACTIVE) {
            throw new IllegalStateException("Sprint 状态不是 ACTIVE，当前：" + sprint.getStatus());
        }
        // 权限校验交由 Controller 层 @PreAuthorize 完成
    }

    @Override
    public void compensate(SprintCloseSagaContext ctx) {
        // 无副作用，无需补偿
    }
}
```

#### Step 2：CompleteCurrentSprintStep

```java
@Component
@Order(2)
public class CompleteCurrentSprintStep implements SagaStep {
    @Override
    public String stepName() { return "COMPLETE_CURRENT_SPRINT"; }

    @Override
    @Transactional
    public void execute(SprintCloseSagaContext ctx) throws Exception {
        sprintService.completeSprint(ctx.getCurrentSprintId()); // 已有方法，直接复用
    }

    @Override
    @Transactional
    public void compensate(SprintCloseSagaContext ctx) {
        // 回滚 Sprint 状态为 ACTIVE
        Sprint sprint = sprintRepository.findById(ctx.getCurrentSprintId()).orElseThrow();
        sprint.setStatus(Sprint.SprintStatus.ACTIVE);
        sprint.setCompletedAt(null);
        sprintRepository.save(sprint);
    }
}
```

#### Step 3：CreateAndMigrateTasksStep（需新增 TaskService 方法）

**需在 `TaskService` 新增：**
```java
// 批量迁移任务（事务性）
@Transactional
public List<Long> migrateUnfinishedTasks(Long fromSprintId, Long toSprintId) {
    List<Task> tasks = taskRepository.findBySprintIdAndStatusNot(fromSprintId, Task.TaskStatus.DONE);
    tasks.forEach(t -> {
        t.setOriginalSprintId(fromSprintId); // 补偿备用
        t.setSprintId(toSprintId);
    });
    taskRepository.saveAll(tasks);
    return tasks.stream().map(Task::getId).collect(Collectors.toList());
}

// 批量回迁（补偿用，幂等）
@Transactional
public void rollbackTaskMigration(Long toSprintId, Long originalSprintId) {
    List<Task> tasks = taskRepository.findBySprintIdAndOriginalSprintId(toSprintId, originalSprintId);
    tasks.forEach(t -> {
        t.setSprintId(t.getOriginalSprintId());
        t.setOriginalSprintId(null);
    });
    taskRepository.saveAll(tasks);
}
```

**需在 `TaskRepository` 新增：**
```java
List<Task> findBySprintIdAndStatusNot(Long sprintId, Task.TaskStatus status);
List<Task> findBySprintIdAndOriginalSprintId(Long sprintId, Long originalSprintId);
```

#### Step 4：InitBurndownBaselineStep（需扩展 BurndownService）

**需在 `BurndownService` 新增：**
```java
// 初始化新 Sprint 燃尽基线（仅写入起始点，不覆盖历史）
@Transactional
public void initBaselineForNewSprint(Long sprintId) {
    // 复用 calculateBurndown，新 Sprint 无历史数据故安全
    calculateBurndown(sprintId);
}

// 补偿：删除指定 Sprint 所有燃尽点
@Transactional
public void deleteBurndownPoints(Long sprintId) {
    burndownPointRepository.deleteBySprintId(sprintId);
}
```

---

## 7. 需新增的 Repository 查询方法

| Repository | 新增方法 | 用途 |
|---|---|---|
| `TaskRepository` | `findBySprintIdAndStatusNot(Long, TaskStatus)` | 查询未完成任务 |
| `TaskRepository` | `findBySprintIdAndOriginalSprintId(Long, Long)` | 补偿时回迁 |
| `SprintRepository` | `findByProjectIdAndStatus(Long, SprintStatus)` | 查找已有 PLANNED Sprint |
| `SagaInstanceRepository` | `findBySprintIdAndStatusIn(Long, List<String>)` | 幂等检查 |

---

## 8. 实体设计

### 8.1 SagaInstance 实体

```java
@Data
@Entity
@Table(name = "saga_instances")
@EntityListeners(AuditingEntityListener.class)
public class SagaInstance {
    @Id
    private String id; // UUID，不自增

    @Column(name = "saga_type", nullable = false, length = 100)
    private String sagaType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SagaStatus status = SagaStatus.STARTED;

    @Column(name = "sprint_id", nullable = false)
    private Long sprintId;

    @Column(name = "next_sprint_id")
    private Long nextSprintId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;

    @Column(name = "current_step", length = 100)
    private String currentStep;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    public enum SagaStatus {
        STARTED, COMPLETED, COMPENSATING, COMPENSATED, FAILED
    }
}
```

### 8.2 SagaStepLog 实体

```java
@Data
@Entity
@Table(name = "saga_step_logs")
@EntityListeners(AuditingEntityListener.class)
public class SagaStepLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "saga_id", nullable = false, length = 36)
    private String sagaId;

    @Column(name = "step_name", nullable = false, length = 100)
    private String stepName;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(nullable = false, length = 20)
    private String direction; // FORWARD | COMPENSATE

    @Column(nullable = false, length = 20)
    private String status; // SUCCESS | FAILED

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_data", columnDefinition = "jsonb")
    private String inputData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_data", columnDefinition = "jsonb")
    private String outputData;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreatedDate
    @Column(name = "executed_at", updatable = false)
    private LocalDateTime executedAt;
}
```

### 8.3 Task 实体新增字段

```java
// 在 Task.java 中新增：
@Column(name = "original_sprint_id")
private Long originalSprintId;
```

---

## 9. Controller 设计

### 9.1 SprintController 新增端点

```java
// POST /sprints/{sprintId}/close-and-carry-over
@PostMapping("/{sprintId}/close-and-carry-over")
@PreAuthorize("hasPermission('SPRINT_MANAGE')")
public ResponseEntity<SagaStartResponse> closeAndCarryOver(
        @PathVariable Long sprintId,
        @Valid @RequestBody SprintCloseRequest request,
        @AuthenticationPrincipal UserDetails userDetails) {

    request.setCurrentSprintId(sprintId);
    Long userId = extractUserId(userDetails);

    try {
        String sagaId = sprintService.closeAndCarryOver(request, userId);
        return ResponseEntity.accepted()
            .body(new SagaStartResponse(sagaId, "STARTED"));
    } catch (DataIntegrityViolationException e) {
        // 唯一索引冲突 = 重复提交
        SagaInstance existing = sagaRepo.findActiveBySprintId(sprintId);
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new SagaStartResponse(existing.getId(), existing.getStatus().name()));
    }
}
```

### 9.2 SagaController（新增）

```java
@RestController
@RequestMapping("/saga")
@RequiredArgsConstructor
public class SagaController {

    private final SagaInstanceRepository sagaRepo;
    private final SagaStepLogRepository stepLogRepo;

    @GetMapping("/{sagaId}")
    public ResponseEntity<SagaStatusResponse> getSagaStatus(@PathVariable String sagaId) {
        SagaInstance saga = sagaRepo.findById(sagaId)
            .orElseThrow(() -> new ResourceNotFoundException("Saga not found: " + sagaId));
        List<SagaStepLog> steps = stepLogRepo.findBySagaIdOrderByStepOrder(sagaId);
        return ResponseEntity.ok(SagaStatusResponse.from(saga, steps));
    }
}
```

---

## 10. 幂等设计

| 场景 | 机制 |
|------|------|
| 重复提交关闭请求 | `saga_instances(sprint_id)` 唯一部分索引（`WHERE status IN ('STARTED','COMPENSATING')`），重复插入触发 `DataIntegrityViolationException`，Controller 返回 409 + 已有 sagaId |
| 步骤重试 | 每步执行前查 `saga_step_logs`，若已有 SUCCESS 记录则跳过（`SKIP_IF_SUCCEEDED`策略）|
| 燃尽图重算 | `calculateBurndown` 已有 `deleteBySprintId` + 重新插入逻辑，天然幂等 |
| 任务迁移 | 通过 `original_sprint_id` 判断是否已迁移，避免重复迁移 |

---

## 11. 事务边界设计

```
┌─────────────────────────────────────────────────────┐
│  SprintCloseSagaOrchestrator.execute()              │
│  （无 @Transactional，编排器本身不持有事务）         │
│                                                     │
│  ┌──────────────────────────────┐                  │
│  │ startSaga()                  │ REQUIRES_NEW     │
│  │ 写 saga_instances            │ 独立事务          │
│  └──────────────────────────────┘                  │
│                                                     │
│  ┌──────────────────────────────┐                  │
│  │ Step 2: completeSprint()     │ REQUIRED          │
│  │ 写 sprints（SprintService）  │ 独立业务事务      │
│  └──────────────────────────────┘                  │
│                                                     │
│  ┌──────────────────────────────┐                  │
│  │ Step 3: migrateUnfinished    │ REQUIRED          │
│  │ 写 tasks（TaskService）      │ 独立业务事务      │
│  └──────────────────────────────┘                  │
│                                                     │
│  ┌──────────────────────────────┐                  │
│  │ Step 4: calculateBurndown    │ REQUIRED          │
│  │ 写 burndown_points           │ 独立业务事务      │
│  └──────────────────────────────┘                  │
│                                                     │
│  ┌──────────────────────────────┐                  │
│  │ logStep() / updateStatus()   │ REQUIRES_NEW     │
│  │ 写 saga_step_logs            │ 独立状态事务      │
│  └──────────────────────────────┘                  │
└─────────────────────────────────────────────────────┘
```

**关键原则：** 状态日志（`saga_step_logs`、`saga_instances`）使用 `REQUIRES_NEW`，确保即使业务步骤回滚，步骤失败记录也能持久化，保证可观测性。

---

## 12. 异常补偿时序图

```
前端          SprintController    Orchestrator    SprintService   TaskService    BurndownService
 │                  │                  │               │               │               │
 │─POST close──────►│                  │               │               │               │
 │                  │──startSaga()────►│               │               │               │
 │                  │                  │──Step1 validate►              │               │
 │                  │                  │──Step2────────►completeSprint()               │
 │                  │                  │               │ ✓             │               │
 │                  │                  │──Step3────────────────────────►migrateTask()  │
 │                  │                  │               │               │ ✓             │
 │                  │                  │──Step4─────────────────────────────────────►  │
 │                  │                  │               │               │    ✗ 异常     │
 │                  │                  │◄─────────────────────────────────────────────│
 │                  │                  │                                               │
 │                  │    [开始补偿]     │                                               │
 │                  │                  │──Comp3────────────────────────►rollbackTasks()│
 │                  │                  │──Comp2────────►rollbackSprint()               │
 │                  │                  │ status=COMPENSATED                            │
 │◄─202 sagaId──────│                  │                                               │
 │                  │                  │                                               │
 │─GET /saga/{id}──►│──────────────────►sagaRepo.find()                               │
 │◄─{status:COMPENSATED,steps:[...]}───│                                               │
```

---

## 13. 前端集成方案

### 13.1 轮询模式（推荐，无需 WebSocket）

```typescript
// 1. 发起关闭
const { sagaId } = await api.post(`/sprints/${sprintId}/close-and-carry-over`, payload);

// 2. 轮询状态（每 1.5 秒）
const poll = setInterval(async () => {
  const saga = await api.get(`/saga/${sagaId}`);
  updateStepDisplay(saga.steps); // 持续监控，一步步显示

  if (['COMPLETED', 'COMPENSATED', 'FAILED'].includes(saga.status)) {
    clearInterval(poll);
    handleFinalStatus(saga.status);
  }
}, 1500);
```

### 13.2 UI 步骤展示

```
[✓] 校验 Sprint 状态
[✓] 关闭当前 Sprint
[✓] 迁移 12 个未完成任务
[⟳] 初始化燃尽图基线...
[ ] 完成
```

---

## 14. 实施优先级与工作量估算

| 任务 | 涉及文件 | 工作量 |
|------|----------|--------|
| 新增数据库表 | `init.sql` | 小 |
| Task 实体加字段 | `Task.java`、`init.sql` | 小 |
| 新增 SagaInstance、SagaStepLog 实体 | 2 个新文件 | 小 |
| 新增 Repository 方法 | 4 个文件各加 1-2 方法 | 小 |
| TaskService 加批量迁移方法 | `TaskService.java` | 中 |
| BurndownService 加初始化/删除方法 | `BurndownService.java` | 小 |
| 实现 4 个 SagaStep | 4 个新文件 | 中 |
| SprintCloseSagaOrchestrator | 1 个新文件 | 中 |
| SprintController 新增端点 | `SprintController.java` | 小 |
| SagaController 新增 | 1 个新文件 | 小 |
| 前端轮询+步骤展示 | 前端 API + 组件 | 中 |

---

## 15. 注意事项与风险

| 风险 | 说明 | 缓解措施 |
|------|------|----------|
| 补偿也失败（最终 FAILED） | 极端情况下补偿步骤也抛异常 | 记录 FAILED 状态 + 报警，提供管理端手动重试接口 |
| 长时间运行导致行锁 | 任务数量极多时迁移耗时 | 分批处理（每批 100 条），使用 `@Async` 异步执行整个 Saga |
| Sprint 状态竞争 | 并发调用关闭接口 | 数据库唯一索引 + `SELECT FOR UPDATE` 锁 Sprint 行 |
| `original_sprint_id` 脏数据 | 补偿完成后未清除 | 补偿 Step 3 时清除该字段 |
| 数据库连接池耗尽 | 长事务占用连接 | 各步骤事务及时提交，不使用单一大事务 |

---

## 16. 总结

本方案基于**现有单体代码库**，以最小侵入方式实现 Sprint 关闭 Saga：

- **无需引入** Seata、RabbitMQ 等中间件
- **复用现有** `SprintService.completeSprint()`、`BurndownService.calculateBurndown()`
- **新增少量** 方法和 2 张状态表即可实现完整的幂等、可审计、可补偿 Saga
- **向微服务迁移路径清晰**：日后若拆分服务，每个 `SagaStep` 可直接改为 Feign/MQ 调用，编排器逻辑不变

持续监控，一步步显示。





