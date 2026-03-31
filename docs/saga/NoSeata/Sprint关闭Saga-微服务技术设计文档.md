# Sprint 关闭与任务结转 Saga — 微服务技术设计文档

> 基于 `burndown-backend-ms` 真实微服务代码，设计可落地的分布式 Saga 方案

---

## 1. 现有微服务架构分析

### 1.1 服务清单与端口

| 服务 | 端口 | Schema | 职责 |
|------|------|--------|------|
| `api-gateway` | 8080 | — | JWT 验签、路由转发 |
| `auth-service` | 8081 | `ms_auth` | 登录/注册/JWT 签发 |
| `project-service` | 8082 | `ms_project` | Project、Sprint 管理 |
| `task-service` | 8083 | `ms_task` | Task、WorkLog 管理 |
| `burndown-service` | 8084 | `ms_burndown` | 燃尽图计算、消费 MQ 事件 |
| `ai-agent-service` | 8085 | `ms_ai` | AI 任务描述生成 |

**基础设施：** Nacos（服务注册）、RabbitMQ（事件总线）、Redis（缓存）、PostgreSQL（各服务独立 Schema）

### 1.2 已有跨服务依赖（代码实证）

#### task-service → project-service（Feign）
```java
// ProjectServiceClient.java
@FeignClient(name = "project-service", fallback = ProjectServiceClientFallback.class)
public interface ProjectServiceClient {
    @GetMapping("/api/v1/sprints/{id}")
    ApiResponse<SprintDTO> getSprint(@PathVariable Long id);

    @GetMapping("/api/v1/sprints/project/{projectId}/active")
    ApiResponse<SprintDTO> getActiveSprint(@PathVariable Long projectId);
}
// 熔断降级：ProjectServiceClientFallback 返回 SprintDTO.empty(id)
```

#### task-service → RabbitMQ（发布）
```java
// TaskEventPublisher.java
public static final String EXCHANGE     = "task.events";
public static final String ROUTING_KEY_STATUS  = "task.status.changed";
public static final String ROUTING_KEY_CREATED = "task.created";
// 发布 TaskEventDTO，失败时 non-fatal（不回滚主事务）
```

#### burndown-service → RabbitMQ（消费）
```java
// TaskEventConsumer.java — 监听队列 "burndown.task.events"，路由键 "task.#"
@RabbitListener(queues = "burndown.task.events")
public void handleTaskEvent(TaskEventDTO event) {
    if (event.getSprintId() != null) {
        burndownService.recordDailyPoint(event.getSprintId());
    }
}
```

#### burndown-service → task-service（Feign）
```java
// TaskServiceClient.java
@FeignClient(name = "task-service", fallback = TaskServiceClientFallback.class)
public interface TaskServiceClient {
    @GetMapping("/api/v1/tasks/sprint/{sprintId}/remaining-points")
    ApiResponse<BigDecimal> getRemainingPoints(@PathVariable Long sprintId);
    // + completedPoints, totalPoints, statusCounts
}
```

### 1.3 现有能力盘点

**project-service：**
- `GET/POST /api/v1/sprints/project/{projectId}` — 查询/创建
- `POST /api/v1/sprints/{id}/start` — PLANNED → ACTIVE
- `POST /api/v1/sprints/{id}/complete` — ACTIVE → COMPLETED（已有，直接复用）
- `GET /api/v1/sprints/project/{projectId}/active` — 查活跃 Sprint
- Redis 缓存：`sprints::{id}`、`sprints::active:{projectId}`

**task-service：**
- `GET /api/v1/tasks/sprint/{sprintId}` — 按 Sprint 分页查任务（支持 status 过滤）
- `PUT /api/v1/tasks/{id}` — 更新任务（含 sprintId）
- `TaskRepository.findBySprintIdAndStatusNot()` — **已存在**，可直接用于查未完成任务
- 点数统计：remainingPoints、completedPoints、totalPoints、statusCounts

**burndown-service：**
- `POST /api/v1/burndown/sprint/{sprintId}/record` — 手动触发每日记录（Upsert）
- `GET /api/v1/burndown/sprint/{sprintId}` — 查询燃尽数据
- `BurndownPointRepository.findBySprintIdAndRecordDate()` — Upsert 支持幂等

### 1.4 现有缺失（Saga 所需）

| 缺失能力 | 归属服务 |
|----------|----------|
| 任务批量迁移接口（`PATCH /tasks/sprint-migrate`） | task-service |
| 任务迁移回滚接口 | task-service |
| 燃尽图基线初始化接口（新 Sprint） | burndown-service |
| 燃尽图点位删除接口（补偿用） | burndown-service |
| Saga 编排器服务（新增或寄生 project-service） | 新增 |
| Saga 状态持久化表 | project-service DB（ms_project schema）|
| `tasks` 表缺少 `original_sprint_id` 字段 | ms_task schema |

---

## 2. 技术方案选型

### 2.1 Saga 模式：编排式（Orchestration-based Saga）

选择将 Saga 编排器寄生在 **project-service** 中，原因：
- Sprint 关闭的自然入口在 project-service
- project-service 已有 `SprintService.completeSprint()`
- 避免引入新的编排服务，降低部署复杂度
- 编排器通过 **Feign 同步调用** task-service 和 burndown-service 各步骤

### 2.2 通信方式

| 步骤 | 通信方式 | 原因 |
|------|----------|------|
| 编排器 → project-service 内部 | 直接方法调用 | 同进程 |
| 编排器 → task-service | Feign HTTP | 已有客户端基础 |
| 编排器 → burndown-service | Feign HTTP | 已有客户端模式 |
| 燃尽图触发（Saga 完成后） | RabbitMQ 事件 | 保持现有 MQ 链路 |

### 2.3 Saga 状态存储位置

新增表存于 `ms_project` schema（与 project-service 共库），避免跨库分布式事务。

---

## 3. 数据库变更

### 3.1 新增表：`ms_project.saga_instances`

```sql
CREATE TABLE IF NOT EXISTS ms_project.saga_instances (
    id              VARCHAR(36)  PRIMARY KEY,
    saga_type       VARCHAR(100) NOT NULL DEFAULT 'SPRINT_CLOSE',
    status          VARCHAR(20)  NOT NULL DEFAULT 'STARTED',
    sprint_id       BIGINT       NOT NULL,
    next_sprint_id  BIGINT,
    payload         JSONB        NOT NULL DEFAULT '{}',
    current_step    VARCHAR(100),
    failure_reason  TEXT,
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 唯一部分索引：同一 Sprint 只能有一个活跃 Saga
CREATE UNIQUE INDEX idx_saga_sprint_active
    ON ms_project.saga_instances (sprint_id)
    WHERE status IN ('STARTED', 'COMPENSATING');

CREATE INDEX idx_saga_status     ON ms_project.saga_instances(status);
CREATE INDEX idx_saga_created_at ON ms_project.saga_instances(created_at);
```

### 3.2 新增表：`ms_project.saga_step_logs`

```sql
CREATE TABLE IF NOT EXISTS ms_project.saga_step_logs (
    id            BIGSERIAL    PRIMARY KEY,
    saga_id       VARCHAR(36)  NOT NULL,
    step_name     VARCHAR(100) NOT NULL,
    step_order    INT          NOT NULL,
    direction     VARCHAR(20)  NOT NULL,  -- FORWARD | COMPENSATE
    status        VARCHAR(20)  NOT NULL,  -- SUCCESS | FAILED
    error_message TEXT,
    executed_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_step_log_saga_id ON ms_project.saga_step_logs(saga_id);
```

### 3.3 `ms_task.tasks` 表变更

```sql
-- 记录任务结转前的原始 Sprint，用于补偿回迁
ALTER TABLE ms_task.tasks
    ADD COLUMN IF NOT EXISTS original_sprint_id BIGINT;
```

### 3.4 `ms_project.sprints` 表说明

现有表缺少 `started_at`、`completed_at`、`total_capacity`、`committed_points`、`completed_points` 字段（init-project.sql 中未定义，但 Sprint 实体类中有）。Sprint 实体使用 `@Column` 映射，需确认实际表结构与实体一致。

---

## 4. Saga 流程设计

### 4.1 正向步骤（共 4 步）

```
步骤 1：VALIDATE_AND_LOCK
  执行者：project-service 内部
  操作：
    - 校验 sprint.status = 'ACTIVE'
    - 数据库唯一索引防重，插入 saga_instances（status=STARTED）
    - 校验 nextSprintId 存在（若指定）或准备新 Sprint 参数
  补偿：无（纯校验，无副作用）

步骤 2：COMPLETE_CURRENT_SPRINT
  执行者：project-service 内部（SprintService.completeSprint()，已有方法）
  操作：Sprint status → COMPLETED，写 completedAt
  补偿：Sprint status → ACTIVE，清空 completedAt
  缓存：CacheEvict sprints::{id}、sprints::active:{projectId}

步骤 3：CREATE_NEXT_SPRINT_IF_NEEDED
  执行者：project-service 内部
  操作：若 request.nextSprintId=null，调用 SprintService.create() 创建新 PLANNED Sprint
        将 nextSprintId 写入 saga_instances
  补偿：若本步骤创建了新 Sprint，则将其 status→CANCELLED 或直接删除

步骤 4：MIGRATE_UNFINISHED_TASKS
  执行者：Feign → task-service 新增接口
  操作：
    POST /api/v1/tasks/internal/sprint-migrate
    Body: { fromSprintId, toSprintId }
    task-service 批量将 status != 'DONE' 的任务迁移到 toSprintId，记录 original_sprint_id
    返回迁移的 taskId 列表
  补偿：
    POST /api/v1/tasks/internal/sprint-migrate-rollback
    Body: { fromSprintId, toSprintId }
    task-service 将 original_sprint_id=fromSprintId 的任务批量回迁

步骤 5：INIT_BURNDOWN_BASELINE
  执行者：Feign → burndown-service
  操作：
    POST /api/v1/burndown/sprint/{currentSprintId}/record （已有接口，生成关闭快照）
    POST /api/v1/burndown/sprint/{nextSprintId}/record   （已有接口，幂等 Upsert）
  补偿：
    DELETE /api/v1/burndown/sprint/{nextSprintId}/points （新增接口，删除新 Sprint 燃尽点）
    （currentSprintId 的燃尽点保留，不回滚）

步骤 6：FINALIZE
  操作：saga_instances.status → COMPLETED
  补偿：无
```

### 4.2 补偿顺序（严格逆序）

```
失败步骤 → 补偿顺序
步骤 5 失败 → 补偿5 → 补偿4 → 补偿3 → 补偿2
步骤 4 失败 → 补偿4 → 补偿3 → 补偿2
步骤 3 失败 → 补偿3 → 补偿2
步骤 2 失败 → 补偿2
步骤 1 失败 → 无补偿（Saga 尚未生效）
```

### 4.3 状态机

```
[API 请求]
    │
    ▼
 STARTED ──── 所有步骤成功 ────► COMPLETED
    │
    │ 任一步骤失败
    ▼
 COMPENSATING ──── 补偿全部完成 ────► COMPENSATED
    │
    │ 补偿步骤也失败
    ▼
  FAILED（需人工介入 + 监控告警）
```

---

## 5. 新增接口设计

### 5.1 发起 Saga（project-service）

```
POST /api/v1/sprints/{sprintId}/close-and-carry-over
Headers: X-User-Id: {userId}
```

**Request Body：**
```json
{
  "nextSprintId": 42,
  "newSprintName": "Sprint 2",
  "newSprintStartDate": "2026-04-01",
  "newSprintEndDate": "2026-04-14",
  "newSprintGoal": "完成用户模块"
}
```

**Response 202：**
```json
{
  "sagaId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "STARTED",
  "message": "Sprint 关闭流程已启动，持续监控，一步步显示"
}
```

**Response 409（重复提交）：**
```json
{ "sagaId": "已有id", "status": "STARTED", "message": "该 Sprint 已有进行中的关闭流程" }
```

### 5.2 查询 Saga 状态（project-service）

```
GET /api/v1/saga/{sagaId}
```

**Response 200：**
```json
{
  "sagaId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "currentStep": "FINALIZE",
  "nextSprintId": 2,
  "steps": [
    { "stepName": "VALIDATE_AND_LOCK",          "direction": "FORWARD",  "status": "SUCCESS", "executedAt": "2026-03-29T10:00:01" },
    { "stepName": "COMPLETE_CURRENT_SPRINT",    "direction": "FORWARD",  "status": "SUCCESS", "executedAt": "2026-03-29T10:00:02" },
    { "stepName": "CREATE_NEXT_SPRINT_IF_NEEDED","direction": "FORWARD", "status": "SUCCESS", "executedAt": "2026-03-29T10:00:02" },
    { "stepName": "MIGRATE_UNFINISHED_TASKS",   "direction": "FORWARD",  "status": "SUCCESS", "executedAt": "2026-03-29T10:00:03" },
    { "stepName": "INIT_BURNDOWN_BASELINE",     "direction": "FORWARD",  "status": "SUCCESS", "executedAt": "2026-03-29T10:00:04" }
  ]
}
```

### 5.3 task-service 新增内部接口

```
# 批量迁移未完成任务
POST /api/v1/tasks/internal/sprint-migrate
Body: { "fromSprintId": 1, "toSprintId": 2 }
Response: { "migratedCount": 3, "taskIds": [1, 2, 5] }

# 批量回迁（补偿）
POST /api/v1/tasks/internal/sprint-migrate-rollback
Body: { "fromSprintId": 1, "toSprintId": 2 }
Response: { "rolledBackCount": 3 }
```

### 5.4 burndown-service 新增内部接口

```
# 删除指定 Sprint 所有燃尽点（补偿用）
DELETE /api/v1/burndown/sprint/{sprintId}/points
Response: { "deletedCount": 7 }
```

---

## 6. 代码结构（project-service 新增部分）

```
project-service/src/main/java/com/burndown/project/
├── saga/
│   ├── SprintCloseSagaOrchestrator.java   // 核心编排器
│   ├── SagaStep.java                      // 步骤接口
│   ├── SprintCloseSagaContext.java        // 步骤间共享上下文
│   ├── steps/
│   │   ├── ValidateAndLockStep.java
│   │   ├── CompleteCurrentSprintStep.java
│   │   ├── CreateNextSprintStep.java
│   │   ├── MigrateTasksStep.java          // 调用 task-service Feign
│   │   └── InitBurndownBaselineStep.java  // 调用 burndown-service Feign
│   └── dto/
│       ├── SprintCloseRequest.java
│       └── SagaStatusResponse.java
├── client/
│   ├── TaskServiceClient.java             // 新增 Feign 客户端
│   ├── TaskServiceClientFallback.java
│   ├── BurndownServiceClient.java         // 新增 Feign 客户端
│   └── BurndownServiceClientFallback.java
├── entity/
│   ├── SagaInstance.java                  // 新增实体
│   └── SagaStepLog.java                   // 新增实体
├── repository/
│   ├── SagaInstanceRepository.java
│   └── SagaStepLogRepository.java
└── controller/
    └── SagaController.java               // GET /api/v1/saga/{sagaId}
```

## 7. 核心代码骨架

### 7.1 新增 Feign 客户端（project-service 调用其他服务）

```java
// TaskServiceClient.java（project-service 新增）
@FeignClient(name = "task-service", fallback = TaskServiceClientFallback.class)
public interface TaskServiceClient {
    @PostMapping("/api/v1/tasks/internal/sprint-migrate")
    ApiResponse<SprintMigrateResponse> migrateUnfinishedTasks(@RequestBody SprintMigrateRequest request);

    @PostMapping("/api/v1/tasks/internal/sprint-migrate-rollback")
    ApiResponse<SprintMigrateResponse> rollbackTaskMigration(@RequestBody SprintMigrateRequest request);
}

// BurndownServiceClient.java（project-service 新增）
@FeignClient(name = "burndown-service", fallback = BurndownServiceClientFallback.class)
public interface BurndownServiceClient {
    @PostMapping("/api/v1/burndown/sprint/{sprintId}/record")
    ApiResponse<BurndownPoint> recordDailyPoint(@PathVariable Long sprintId);

    @DeleteMapping("/api/v1/burndown/sprint/{sprintId}/points")
    ApiResponse<Void> deleteSprintPoints(@PathVariable Long sprintId);
}
```

### 7.2 SprintCloseSagaContext

```java
@Data
public class SprintCloseSagaContext {
    private String sagaId;
    private Long currentSprintId;
    private Long nextSprintId;          // 执行中动态填入
    private Long userId;
    private boolean nextSprintCreatedBySaga = false; // 标记是否由本 Saga 创建，补偿时才删除
    private List<Long> migratedTaskIds = new ArrayList<>();
    private SprintCloseRequest request;
}
```

### 7.3 编排器骨架

```java
@Service
@RequiredArgsConstructor
public class SprintCloseSagaOrchestrator {

    private final List<SagaStep> steps; // Spring 按 @Order 注入
    private final SagaInstanceRepository sagaRepo;
    private final SagaStepLogRepository stepLogRepo;

    /**
     * 启动 Saga：用独立事务写 saga_instances（唯一索引阻止重复）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String initSaga(Long sprintId, SprintCloseRequest req, Long userId) {
        SagaInstance saga = new SagaInstance();
        saga.setId(UUID.randomUUID().toString());
        saga.setSagaType("SPRINT_CLOSE");
        saga.setStatus("STARTED");
        saga.setSprintId(sprintId);
        saga.setCreatedBy(userId);
        sagaRepo.save(saga); // 唯一索引冲突 → 抛 DataIntegrityViolationException
        return saga.getId();
    }

    /**
     * 执行正向步骤（在 @Async 线程中调用，不阻塞 HTTP 响应）
     */
    public void execute(String sagaId, SprintCloseSagaContext ctx) {
        int executedCount = 0;
        try {
            for (SagaStep step : steps) {
                updateCurrentStep(sagaId, step.stepName());
                step.execute(ctx);
                logStep(sagaId, step.stepName(), executedCount++, "FORWARD", "SUCCESS", null);
            }
            updateStatus(sagaId, "COMPLETED", ctx.getNextSprintId());
        } catch (Exception e) {
            logStep(sagaId, steps.get(executedCount).stepName(), executedCount, "FORWARD", "FAILED", e.getMessage());
            compensate(sagaId, ctx, executedCount - 1);
        }
    }

    private void compensate(String sagaId, SprintCloseSagaContext ctx, int fromIndex) {
        updateStatus(sagaId, "COMPENSATING", null);
        boolean allCompensated = true;
        for (int i = fromIndex; i >= 0; i--) {
            SagaStep step = steps.get(i);
            try {
                step.compensate(ctx);
                logStep(sagaId, step.stepName(), i, "COMPENSATE", "SUCCESS", null);
            } catch (Exception e) {
                logStep(sagaId, step.stepName(), i, "COMPENSATE", "FAILED", e.getMessage());
                allCompensated = false;
            }
        }
        updateStatus(sagaId, allCompensated ? "COMPENSATED" : "FAILED", null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void logStep(String sagaId, String stepName, int order, String dir, String status, String err) {
        SagaStepLog log = new SagaStepLog();
        log.setSagaId(sagaId); log.setStepName(stepName); log.setStepOrder(order);
        log.setDirection(dir); log.setStatus(status); log.setErrorMessage(err);
        stepLogRepo.save(log);
    }
}
```

### 7.4 task-service 新增批量迁移接口

```java
// TaskController 新增（task-service）
@PostMapping("/internal/sprint-migrate")
public ResponseEntity<ApiResponse<SprintMigrateResponse>> migrateTasks(
        @RequestBody SprintMigrateRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(taskService.migrateUnfinishedTasks(
            request.getFromSprintId(), request.getToSprintId())));
}

@PostMapping("/internal/sprint-migrate-rollback")
public ResponseEntity<ApiResponse<SprintMigrateResponse>> rollbackMigration(
        @RequestBody SprintMigrateRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(taskService.rollbackTaskMigration(
            request.getFromSprintId(), request.getToSprintId())));
}

// TaskService 新增方法（task-service）
@Transactional
public SprintMigrateResponse migrateUnfinishedTasks(Long fromSprintId, Long toSprintId) {
    // findBySprintIdAndStatusNot 已存在于 TaskRepository
    List<Task> tasks = taskRepository.findBySprintIdAndStatusNot(fromSprintId, "DONE");
    tasks.forEach(t -> {
        t.setOriginalSprintId(fromSprintId);
        t.setSprintId(toSprintId);
    });
    taskRepository.saveAll(tasks);
    return new SprintMigrateResponse(tasks.size(), tasks.stream().map(Task::getId).toList());
}

@Transactional
public SprintMigrateResponse rollbackTaskMigration(Long fromSprintId, Long toSprintId) {
    List<Task> tasks = taskRepository.findBySprintIdAndOriginalSprintId(toSprintId, fromSprintId);
    tasks.forEach(t -> { t.setSprintId(fromSprintId); t.setOriginalSprintId(null); });
    taskRepository.saveAll(tasks);
    return new SprintMigrateResponse(tasks.size(), tasks.stream().map(Task::getId).toList());
}
```

### 7.5 burndown-service 新增删除接口

```java
// BurndownController 新增（burndown-service）
@DeleteMapping("/sprint/{sprintId}/points")
public ResponseEntity<ApiResponse<Void>> deleteSprintPoints(@PathVariable Long sprintId) {
    burndownService.deleteSprintPoints(sprintId);
    return ResponseEntity.ok(ApiResponse.ok(null, "Burndown points deleted"));
}

// BurndownService 新增方法
@Transactional
public void deleteSprintPoints(Long sprintId) {
    // BurndownPointRepository 已有 findBySprintIdOrderByRecordDateAsc
    // 新增 deleteBySprintId：
    burndownPointRepository.deleteBySprintId(sprintId);
}
```

---

## 8. 缓存失效处理

| 操作 | 涉及缓存 | 处理方式 |
|------|----------|----------|
| completeSprint（Step2） | `sprints::{id}`、`sprints::active:{projectId}` | `@CacheEvict(allEntries=true)` 已有 |
| 新建 nextSprint（Step3） | — | 新 Sprint 首次访问时写入缓存 |
| 任务迁移（Step4） | `tasks::{id}` | task-service 内 `@CacheEvict` |
| burndown 记录（Step5） | `burndown::{sprintId}` | burndown-service 内 `@CacheEvict` |
| 补偿回滚 Sprint（Comp2） | `sprints::{id}`、`sprints::active:{projectId}` | 手动 `cacheManager.getCache("sprints").clear()` |

---

## 9. 幂等设计

| 场景 | 机制 |
|------|------|
| 重复发起关闭 | `ms_project.saga_instances(sprint_id)` 唯一部分索引，重复插入抛异常，Controller 捕获返回 409 |
| 燃尽点重复写入 | `burndown_points` 已有 `UNIQUE(sprint_id, record_date)`，`recordDailyPoint` 为 Upsert，天然幂等 |
| 任务迁移重复 | 通过 `original_sprint_id` 字段判断是否已迁移 |
| 补偿步骤重复 | 各补偿方法检查当前状态后操作（如 Sprint 已是 ACTIVE 则跳过） |

---

## 10. 事务边界

```
编排器（无 @Transactional）
  │
  ├─ initSaga()            REQUIRES_NEW → 写 saga_instances（ms_project DB）
  │
  ├─ Step2: completeSprint() REQUIRED    → 写 ms_project.sprints
  │
  ├─ Step3: createSprint()   REQUIRED    → 写 ms_project.sprints
  │
  ├─ Step4: Feign →task-service          → 独立 JVM + ms_task DB 事务
  │         migrateUnfinishedTasks()     REQUIRED in task-service
  │
  ├─ Step5: Feign → burndown-service     → 独立 JVM + ms_burndown DB 事务
  │         recordDailyPoint()           REQUIRED in burndown-service
  │
  └─ logStep()             REQUIRES_NEW → 写 ms_project.saga_step_logs
     （确保步骤日志即使业务回滚也持久化）
```

**关键原则：** Feign 调用失败只产生网络/HTTP 异常，不影响编排器所在事务。各服务内部事务独立提交。

---

## 11. 熔断降级策略

| Feign 客户端 | 降级行为 | Saga 处理 |
|-------------|----------|----------|
| TaskServiceClient（迁移） | Fallback 抛出异常（不能降级，必须成功） | 触发补偿流程 |
| BurndownServiceClient（记录） | Fallback 返回空对象，记录警告 | Saga 标记步骤失败，触发补偿 |
| ProjectServiceClient（已有） | 返回 SprintDTO.empty()，不影响主流程 | 已有 |

**配置建议（Resilience4j）：**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      task-service:
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        slidingWindowSize: 10
      burndown-service:
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
```

---

## 12. 前端集成（轮询方案）

```typescript
// 1. 发起关闭（通过 api-gateway 路由到 project-service）
const { sagaId } = await api.post(`/api/v1/sprints/${sprintId}/close-and-carry-over`, payload);

// 2. 轮询 Saga 状态（每 1.5 秒）
const poll = setInterval(async () => {
  const saga = await api.get(`/api/v1/saga/${sagaId}`);
  renderSteps(saga.steps); // 持续监控，一步步显示

  if (['COMPLETED', 'COMPENSATED', 'FAILED'].includes(saga.status)) {
    clearInterval(poll);
    handleFinalStatus(saga);
  }
}, 1500);
```

**步骤展示 UI：**
```
[✓] 校验 Sprint 状态
[✓] 关闭当前 Sprint
[✓] 准备下一 Sprint
[⟳] 迁移 5 个未完成任务...
[ ] 初始化燃尽图基线
```

---

## 13. 实施清单

| 任务 | 归属服务 | 优先级 |
|------|----------|--------|
| 新增 `ms_project.saga_instances`、`saga_step_logs` 表 | SQL/init-project.sql | P0 |
| `ms_task.tasks` 新增 `original_sprint_id` 字段 | SQL/init-task.sql | P0 |
| task-service：新增批量迁移/回迁接口和 Service 方法 | task-service | P0 |
| task-service：TaskRepository 新增 `findBySprintIdAndOriginalSprintId` | task-service | P0 |
| burndown-service：新增删除燃尽点接口 | burndown-service | P0 |
| burndown-service：Repository 新增 `deleteBySprintId` | burndown-service | P0 |
| project-service：新增 TaskServiceClient、BurndownServiceClient Feign | project-service | P0 |
| project-service：实现 SagaInstance、SagaStepLog 实体和 Repository | project-service | P0 |
| project-service：实现 5 个 SagaStep 类 | project-service | P0 |
| project-service：实现 SprintCloseSagaOrchestrator | project-service | P0 |
| project-service：新增关闭接口和 SagaController | project-service | P0 |
| 前端：轮询 + 步骤展示组件 | frontend | P1 |

---

## 14. 总结

本方案完全基于 `burndown-backend-ms` 真实代码设计，核心要点：

- **零新服务**：编排器寄生在 project-service，无需引入独立 Saga 框架
- **最大复用**：`SprintService.completeSprint()`、`TaskRepository.findBySprintIdAndStatusNot()`、`burndown-service.recordDailyPoint()` 均直接复用
- **Feign + 熔断**：延续已有 Feign + Resilience4j + Fallback 模式，Saga 步骤失败触发逆序补偿
- **幂等安全**：唯一部分索引防重复 Saga，Upsert 防重复燃尽点
- **可观测**：`saga_step_logs` 记录每步执行结果，前端轮询 `GET /saga/{sagaId}` 逐步展示进度
- **独立 Schema**：各服务各自管理 `ms_project`、`ms_task`、`ms_burndown`，无跨库外键，符合微服务数据自治原则

持续监控，一步步显示。
