# Sprint 关闭与任务结转 Saga — 微服务测试用例文档

> 基于 `burndown-backend-ms` 真实微服务架构，覆盖各服务单元测试、跨服务集成测试、Feign 熔断测试、Saga 补偿测试、API 测试。

---

## 测试范围总览

| 测试类别 | 覆盖服务 | 用例数 |
|----------|----------|--------|
| 单元测试 - SagaStep | project-service | 18 |
| 单元测试 - 编排器 | project-service | 6 |
| 单元测试 - task-service 新增方法 | task-service | 6 |
| 单元测试 - burndown-service 新增方法 | burndown-service | 4 |
| Feign 熔断降级测试 | project-service | 6 |
| 集成测试 - 正向流程 | 跨服务 | 4 |
| 集成测试 - 补偿流程 | 跨服务 | 5 |
| 幂等测试 | project-service | 4 |
| API 测试（含 Gateway） | 全链路 | 7 |
| 缓存测试 | Redis | 4 |

**合计：64 个测试用例**

---

## 一、单元测试 — SagaStep（project-service）

> 使用 `@ExtendWith(MockitoExtension.class)`，Mock 所有外部依赖

### 1.1 ValidateAndLockStep

#### TC-MS-STEP1-001：Sprint 为 ACTIVE 时校验通过

```
前置条件：
  - SprintRepository.findById(1L) 返回 status="ACTIVE" 的 Sprint
Mock：
  - sagaInstanceRepository.save() 正常
测试步骤：
  1. 调用 ValidateAndLockStep.execute(context)
期望结果：
  - 无异常抛出
  - sagaInstanceRepository.save() 被调用一次
```

#### TC-MS-STEP1-002：Sprint 不为 ACTIVE 时抛出 BusinessException

```
Mock：SprintRepository.findById(1L) 返回 status="COMPLETED"
期望结果：抛出 BusinessException，code="SPRINT_NOT_ACTIVE"
```

#### TC-MS-STEP1-003：Sprint 不存在时抛出 ResourceNotFoundException

```
Mock：SprintRepository.findById(999L) 返回 Optional.empty()
期望结果：抛出 ResourceNotFoundException
```

#### TC-MS-STEP1-004：compensate 无副作用

```
测试步骤：调用 ValidateAndLockStep.compensate(context)
期望结果：不调用任何 Repository，无异常
```

---

### 1.2 CompleteCurrentSprintStep

#### TC-MS-STEP2-001：正向执行调用 SprintService.completeSprint

```
Mock：SprintService.completeSprint(1L) 返回 COMPLETED 的 SprintDTO
期望结果：
  - SprintService.completeSprint(1L) 被调用一次
  - Redis 缓存 sprints::1 被驱逐（@CacheEvict）
```

#### TC-MS-STEP2-002：SprintService 抛异常时向上传播

```
Mock：SprintService.completeSprint(1L) 抛 BusinessException
期望结果：异常向上传播，不被吞掉
```

#### TC-MS-STEP2-003：补偿将 Sprint 回滚为 ACTIVE

```
Mock：SprintRepository.findById(1L) 返回 COMPLETED Sprint
      SprintRepository.save() 正常
期望结果：
  - sprint.status = "ACTIVE"
  - sprint.completedAt = null
  - save() 被调用
```

---

### 1.3 CreateNextSprintStep

#### TC-MS-STEP3-001：nextSprintId 已指定时不创建新 Sprint

```
前置条件：context.request.nextSprintId = 2L
Mock：SprintRepository.findById(2L) 返回 PLANNED Sprint
期望结果：
  - SprintService.create() 未被调用
  - context.nextSprintId = 2L
  - context.nextSprintCreatedBySaga = false
```

#### TC-MS-STEP3-002：nextSprintId 为空时自动创建新 Sprint

```
前置条件：context.request.nextSprintId = null，newSprintName="Sprint 2"
Mock：SprintService.create() 返回 id=10 的新 Sprint
期望结果：
  - context.nextSprintId = 10
  - context.nextSprintCreatedBySaga = true
```

#### TC-MS-STEP3-003：补偿时仅删除由本 Saga 创建的 Sprint

```
场景A：context.nextSprintCreatedBySaga = true
  期望：SprintRepository.deleteById(10L) 被调用
场景B：context.nextSprintCreatedBySaga = false
  期望：SprintRepository.deleteById() 未被调用
```

---

### 1.4 MigrateTasksStep

#### TC-MS-STEP4-001：正向执行调用 task-service Feign

```
Mock：TaskServiceClient.migrateUnfinishedTasks({fromSprintId:1, toSprintId:2})
      返回 { migratedCount:3, taskIds:[1,2,3] }
期望结果：
  - Feign 被调用一次
  - context.migratedTaskIds = [1,2,3]
```

#### TC-MS-STEP4-002：Feign 返回错误时抛出异常

```
Mock：TaskServiceClient.migrateUnfinishedTasks() 抛出 FeignException
期望结果：异常向上传播，触发补偿
```

#### TC-MS-STEP4-003：无未完成任务时正常执行（空迁移）

```
Mock：TaskServiceClient.migrateUnfinishedTasks() 返回 { migratedCount:0, taskIds:[] }
期望结果：正常返回，context.migratedTaskIds = []
```

#### TC-MS-STEP4-004：补偿调用 rollbackTaskMigration

```
Mock：TaskServiceClient.rollbackTaskMigration({fromSprintId:1, toSprintId:2}) 正常
期望结果：Feign rollback 接口被调用一次
```

---

### 1.5 InitBurndownBaselineStep

#### TC-MS-STEP5-001：为当前 Sprint 和新 Sprint 各调用一次 recordDailyPoint

```
Mock：BurndownServiceClient.recordDailyPoint(1L) 正常
      BurndownServiceClient.recordDailyPoint(2L) 正常
期望结果：两次 Feign 调用均发生
```

#### TC-MS-STEP5-002：burndown-service 不可用时抛出异常

```
Mock：BurndownServiceClient.recordDailyPoint(2L) 抛出 FeignException
期望结果：异常向上传播，触发补偿
```

#### TC-MS-STEP5-003：补偿调用 deleteSprintPoints 仅针对新 Sprint

```
Mock：BurndownServiceClient.deleteSprintPoints(2L) 正常
期望结果：
  - deleteSprintPoints(2L) 被调用
  - deleteSprintPoints(1L) 未被调用（原 Sprint 保留）
```

---

## 二、单元测试 — 编排器（project-service）

#### TC-MS-ORCH-001：所有步骤成功，saga status = COMPLETED

```
Mock：5 个 SagaStep.execute() 均正常返回
期望结果：
  - 5 个步骤按 @Order 顺序调用
  - saga_instances.status = "COMPLETED"
  - saga_step_logs 有 5 条 FORWARD/SUCCESS 记录
```

#### TC-MS-ORCH-002：Step4（任务迁移）失败，逆序补偿 Step3/2

```
Mock：Step1/2/3 execute() 正常；Step4.execute() 抛异常
      Step3.compensate()、Step2.compensate() 正常
期望结果：
  - Step4.compensate() 不被调用（本步骤失败，无需补偿自身）
  - Step3.compensate()、Step2.compensate() 依次调用
  - saga_instances.status = "COMPENSATED"
  - saga_step_logs 有 Step4 FORWARD/FAILED 记录
```

#### TC-MS-ORCH-003：Step5（燃尽图）失败，逆序补偿 Step4/3/2

```
Mock：Step1-4 execute() 正常；Step5.execute() 抛异常
期望结果：
  - Step4.compensate()、Step3.compensate()、Step2.compensate() 依次调用
  - saga status = "COMPENSATED"
```

#### TC-MS-ORCH-004：补偿步骤本身失败，saga status = FAILED

```
Mock：Step4.execute() 抛异常（触发补偿）
      Step3.compensate() 抛异常
      Step2.compensate() 正常
期望结果：
  - Step2.compensate() 仍被调用（不因 Step3 补偿失败中断）
  - saga status = "FAILED"
  - Step3 COMPENSATE/FAILED 有日志
```

#### TC-MS-ORCH-005：initSaga 成功写入 saga_instances

```
测试步骤：调用 orchestrator.initSaga(1L, request, 10L)
期望结果：
  - 返回非空 UUID
  - sagaRepo.save() 被调用，记录 sprintId=1，status=STARTED，createdBy=10
```

#### TC-MS-ORCH-006：logStep 使用 REQUIRES_NEW 事务独立提交

```
验证方式：检查 logStep() 方法的 @Transactional(propagation=REQUIRES_NEW) 注解
期望结果：步骤日志写入不受外层事务回滚影响
```

---

## 三、单元测试 — task-service 新增方法

#### TC-MS-TASK-001：migrateUnfinishedTasks 只迁移非 DONE 任务

```
前置条件：Sprint#1 下：Task#1(TODO)、Task#2(IN_PROGRESS)、Task#3(IN_REVIEW)、Task#4(DONE)、Task#5(BLOCKED)
Mock：taskRepository.findBySprintIdAndStatusNot(1L, "DONE") 返回 [#1,#2,#3,#5]
期望结果：
  - saveAll() 入参包含 4 个任务，均设置 sprintId=2，originalSprintId=1
  - Task#4 不在 saveAll 参数中
  - 返回 migratedCount=4
```

#### TC-MS-TASK-002：migrateUnfinishedTasks 事务失败全量回滚

```
Mock：taskRepository.saveAll() 抛出 RuntimeException
期望结果：事务回滚，无任务 sprintId 被修改（@Transactional 保证）
```

#### TC-MS-TASK-003：rollbackTaskMigration 正确回迁任务

```
Mock：taskRepository.findBySprintIdAndOriginalSprintId(2L, 1L) 返回 [Task#1, Task#2]
期望结果：
  - Task#1/2 的 sprintId=1，originalSprintId=null
  - saveAll() 被调用
```

#### TC-MS-TASK-004：rollbackTaskMigration 幂等（无符合条件任务时空操作）

```
Mock：findBySprintIdAndOriginalSprintId(2L, 1L) 返回 []
期望结果：saveAll() 不被调用，方法正常返回
```

#### TC-MS-TASK-005：批量迁移接口返回正确的 taskId 列表

```
Mock：迁移 3 个任务，id=[10,11,12]
期望结果：SprintMigrateResponse.taskIds = [10, 11, 12]
```

#### TC-MS-TASK-006：TaskRepository.findBySprintIdAndStatusNot 已存在，可直接复用

```
验证方式：检查 TaskRepository 中存在 findBySprintIdAndStatusNot(Long sprintId, String status)
期望结果：方法签名存在，无需新增（已在现有代码中）
```

---

## 四、单元测试 — burndown-service 新增方法

#### TC-MS-BD-001：deleteSprintPoints 调用 Repository 删除

```
Mock：burndownPointRepository.deleteBySprintId(2L) 正常
测试步骤：调用 burndownService.deleteSprintPoints(2L)
期望结果：deleteBySprintId(2L) 被调用一次
```

#### TC-MS-BD-002：deleteSprintPoints 幂等（Sprint 无燃尽点时不报错）

```
Mock：deleteBySprintId(99L) 执行 0 行删除，正常返回
期望结果：方法正常返回，不抛异常
```

#### TC-MS-BD-003：recordDailyPoint Upsert 行为（已有记录时更新）

```
前置条件：Sprint#1 在 today 已有 BurndownPoint 记录
Mock：burndownPointRepository.findBySprintIdAndRecordDate(1L, today) 返回已有记录
      TaskServiceClient 返回 remaining=5, completed=10, total=15
期望结果：
  - save() 被调用（更新，非插入）
  - 返回记录的 remainingPoints=5
```

#### TC-MS-BD-004：recordDailyPoint 在 TaskServiceClient 不可用时使用 fetchSafe 降级

```
Mock：TaskServiceClient.getRemainingPoints() 抛出异常
期望结果：
  - remainingPoints = BigDecimal.ZERO（fetchSafe 降级）
  - 方法正常返回，不抛异常
```

---

## 五、Feign 熔断降级测试

#### TC-MS-FEIGN-001：task-service 不可用时 MigrateTasksStep Fallback 抛出异常

```
场景：task-service 下线，Feign 触发 Fallback
验证：TaskServiceClientFallback.migrateUnfinishedTasks() 应抛出 ServiceUnavailableException
期望结果：Step4 执行失败，Saga 触发补偿流程
注意：迁移接口不能静默降级，必须让 Saga 感知失败
```

#### TC-MS-FEIGN-002：burndown-service 不可用时 Fallback 返回空并记录警告

```
场景：burndown-service 下线，Feign 触发 Fallback
验证：BurndownServiceClientFallback.recordDailyPoint() 应抛出异常（非 null 返回）
期望结果：Step5 执行失败，Saga 触发补偿
```

#### TC-MS-FEIGN-003：project-service 在 task-service 中的熔断降级（已有）

```
场景：task-service 创建任务时，project-service 不可用
Mock：ProjectServiceClientFallback.getSprint(1L) 返回 SprintDTO.empty(1L)
期望结果：
  - 任务创建成功（Fallback 不阻断）
  - 日志中有 warn 信息
  - 这是已有行为，验证 Saga 流程不破坏此降级逻辑
```

#### TC-MS-FEIGN-004：Resilience4j 熔断器打开后请求快速失败

```
场景：task-service 连续失败 5 次（超过 slidingWindowSize=10 的 50% 阈值）
期望结果：
  - 熔断器状态变为 OPEN
  - 后续请求不实际发出，直接走 Fallback
  - waitDurationInOpenState=10s 后进入 HALF_OPEN
```

#### TC-MS-FEIGN-005：Feign 超时触发 Fallback

```
场景：task-service 响应超时（超过 connectTimeout/readTimeout）
期望结果：Feign 抛出 RetryableException，Fallback 被触发，Saga 感知失败
```

#### TC-MS-FEIGN-006：补偿阶段 Feign 失败时 Saga 记录 FAILED 而非崩溃

```
场景：Step4 失败触发补偿，补偿时调用 rollbackTaskMigration 也失败
期望结果：
  - 补偿异常被 catch 并记录到 saga_step_logs（COMPENSATE/FAILED）
  - 应用不崩溃
  - saga status = "FAILED"
```

---

## 六、集成测试 — 正向流程

> 使用 Testcontainers 启动 PostgreSQL + RabbitMQ + Redis，各服务以 @SpringBootTest 启动，Feign 调用真实服务（或 WireMock 模拟）

#### TC-MS-INT-001：全流程成功，三个 Schema 数据一致

```
前置条件（数据库）：
  ms_project.sprints: Sprint#1 status=ACTIVE, projectId=1
  ms_task.tasks: Task#1(TODO), Task#2(IN_PROGRESS), Task#3(DONE) sprintId=1
  ms_burndown.burndown_points: 无 Sprint#1 数据
请求：
  POST /api/v1/sprints/1/close-and-carry-over
  X-User-Id: 1
  Body: { "newSprintName": "Sprint 2", "newSprintStartDate": "2026-04-01", "newSprintEndDate": "2026-04-14" }
期望结果（等待 Saga 完成后）：
  ms_project.sprints:
    - Sprint#1 status=COMPLETED，completedAt 不为 null
    - Sprint#2 status=PLANNED 已创建
  ms_task.tasks:
    - Task#1/2 sprintId=Sprint#2.id，originalSprintId=1
    - Task#3 sprintId=1（DONE 不迁移）
  ms_burndown.burndown_points:
    - Sprint#1 有今日快照记录
    - Sprint#2 有基线记录
  ms_project.saga_instances:
    - status=COMPLETED
  ms_project.saga_step_logs:
    - 5 条 FORWARD/SUCCESS 记录
```

#### TC-MS-INT-002：指定已有 PLANNED Sprint 为目标 Sprint

```
前置条件：Sprint#2 status=PLANNED 已存在
请求 Body：{ "nextSprintId": 2 }
期望结果：
  - 未新建 Sprint（仍只有 2 个 Sprint）
  - Task 迁移到 Sprint#2
  - Sprint#2 状态仍为 PLANNED
```

#### TC-MS-INT-003：RabbitMQ 事件触发 burndown-service 自动更新

```
前置条件：task-service、burndown-service 均启动，MQ 连通
测试步骤：
  1. 通过 task-service 修改一个任务状态（触发 task.status.changed 事件）
  2. 等待 burndown-service 消费事件（最多 3 秒）
期望结果：
  - TaskEventConsumer.handleTaskEvent() 被触发
  - ms_burndown.burndown_points 更新今日记录
验证：这是现有 MQ 链路，Saga 流程完成后 MQ 链路应继续正常工作
```

#### TC-MS-INT-004：全流程完成后缓存正确失效

```
测试步骤：
  1. 执行全流程 Saga
  2. GET /api/v1/sprints/1（应反映 COMPLETED 状态）
  3. GET /api/v1/sprints/project/1/active（应返回 null 或新 Sprint）
期望结果：
  - Redis 中 sprints::1 已更新为 COMPLETED
  - sprints::active:1 已失效，不返回旧的 ACTIVE Sprint
```

---

## 七、集成测试 — 补偿流程

#### TC-MS-COMP-001：Step4（任务迁移）失败，Sprint 回滚为 ACTIVE

```
模拟失败：WireMock 将 task-service /internal/sprint-migrate 返回 500
期望结果：
  - ms_project.sprints: Sprint#1 status=ACTIVE（已回滚）
  - ms_task.tasks: 无任务迁移（task-service 返回 500，本服务事务未提交）
  - ms_project.saga_instances: status=COMPENSATED
  - saga_step_logs: Step4 FORWARD/FAILED、Step3/2 COMPENSATE/SUCCESS
```

#### TC-MS-COMP-002：Step5（燃尽图）失败，任务回迁，Sprint 恢复

```
模拟失败：WireMock 将 burndown-service /sprint/2/record 返回 503
期望结果：
  - ms_task.tasks: Task#1/2 sprintId 恢复为 1，originalSprintId=null
  - ms_project.sprints: Sprint#1 status=ACTIVE
  - ms_burndown.burndown_points: Sprint#2 点位被删除（DELETE 接口）
  - saga status = COMPENSATED
```

#### TC-MS-COMP-003：Step5 补偿（deleteSprintPoints）失败，saga status=FAILED

```
模拟失败：
  - burndown-service /sprint/2/record 返回 503（Step5 失败）
  - burndown-service /sprint/2/points DELETE 也返回 503（补偿也失败）
期望结果：
  - saga status = FAILED
  - 应用不崩溃
  - saga_step_logs 中 Step5 COMPENSATE/FAILED 有记录
```

#### TC-MS-COMP-004：Step2（completeSprint）失败，无任何跨服务副作用

```
模拟失败：SprintService.completeSprint() 抛出 BusinessException
期望结果：
  - ms_task.tasks 未发生任何变更（Step4 未执行）
  - ms_burndown.burndown_points 无新记录（Step5 未执行）
  - saga status = COMPENSATED（Step2 补偿：无需操作因尚未修改）
```

#### TC-MS-COMP-005：新建 Sprint 后任务迁移失败，新 Sprint 被删除

```
前置条件：request.nextSprintId=null（需新建 Sprint）
模拟失败：Step4 Feign 失败
期望结果：
  - Step3 补偿：新建的 Sprint 被删除（context.nextSprintCreatedBySaga=true）
  - Sprint#1 恢复为 ACTIVE
  - ms_project.sprints 中没有残留的 PLANNED Sprint
```

---

## 八、幂等测试

#### TC-MS-IDEM-001：同 sprintId 重复提交返回 409 和已有 sagaId

```
测试步骤：
  1. POST /api/v1/sprints/1/close-and-carry-over（第一次，Saga 处于 STARTED）
  2. POST /api/v1/sprints/1/close-and-carry-over（第二次，立刻重发）
期望结果：
  - 第一次：HTTP 202，返回 sagaId
  - 第二次：HTTP 409，body 包含同一 sagaId
  - ms_project.saga_instances 中 sprint_id=1 只有一条活跃记录
  - ms_task.tasks 无重复迁移
验证机制：唯一部分索引 idx_saga_sprint_active 触发 DataIntegrityViolationException
```

#### TC-MS-IDEM-002：Saga COMPLETED 后再次提交返回 400

```
前置条件：Sprint#1 已 COMPLETED，saga status=COMPLETED
测试步骤：POST /api/v1/sprints/1/close-and-carry-over
期望结果：HTTP 400，提示 Sprint 已关闭
```

#### TC-MS-IDEM-003：burndown_points UNIQUE 约束保证 recordDailyPoint 幂等

```
前置条件：Sprint#2 今日已有 burndown_points 记录
测试步骤：连续调用 BurndownService.recordDailyPoint(2L) 两次
期望结果：
  - 第二次调用走 Upsert 更新路径（findBySprintIdAndRecordDate 返回已有记录）
  - burndown_points 中 sprint_id=2 今日只有 1 条记录
  - UNIQUE(sprint_id, record_date) 约束未违反
```

#### TC-MS-IDEM-004：rollbackTaskMigration 重复调用无副作用

```
测试步骤：连续调用 rollbackTaskMigration(1L, 2L) 两次
期望结果：
  - 第一次：正常回迁 tasks
  - 第二次：findBySprintIdAndOriginalSprintId 返回空列表，saveAll([]) 不调用
  - 最终 tasks 状态与第一次调用后一致
```

---

## 九、API 测试（含 Gateway 路由）

> 通过 api-gateway (port 8080) 发起请求，验证完整链路

#### TC-MS-API-001：携带有效 JWT，关闭接口返回 202

```
请求：
  POST http://localhost:8080/api/v1/sprints/1/close-and-carry-over
  Authorization: Bearer <有效 JWT>
  X-User-Id: 1（Gateway 注入或客户端传递）
  Body: { "newSprintName": "Sprint 2", "newSprintStartDate": "2026-04-01", "newSprintEndDate": "2026-04-14" }
期望结果：
  HTTP 202
  Body: { "sagaId": "<UUID>", "status": "STARTED" }
```

#### TC-MS-API-002：无 JWT 请求被 Gateway 拦截，返回 401

```
请求：POST http://localhost:8080/api/v1/sprints/1/close-and-carry-over（无 Authorization）
期望结果：HTTP 401，由 JwtAuthFilter 拦截
```

#### TC-MS-API-003：JWT 过期返回 401

```
请求：使用过期 JWT 调用关闭接口
期望结果：HTTP 401，Gateway JwtAuthFilter 验签失败
```

#### TC-MS-API-004：GET /api/v1/saga/{sagaId} 返回完整步骤信息

```
前置条件：sagaId="abc-123" 对应 COMPLETED Saga，有 5 条步骤日志
请求：GET http://localhost:8080/api/v1/saga/abc-123
期望结果：
  HTTP 200
  Body:
  {
    "sagaId": "abc-123",
    "status": "COMPLETED",
    "nextSprintId": 2,
    "steps": [
      { "stepName": "VALIDATE_AND_LOCK",           "direction": "FORWARD", "status": "SUCCESS" },
      { "stepName": "COMPLETE_CURRENT_SPRINT",     "direction": "FORWARD", "status": "SUCCESS" },
      { "stepName": "CREATE_NEXT_SPRINT_IF_NEEDED","direction": "FORWARD", "status": "SUCCESS" },
      { "stepName": "MIGRATE_UNFINISHED_TASKS",    "direction": "FORWARD", "status": "SUCCESS" },
      { "stepName": "INIT_BURNDOWN_BASELINE",      "direction": "FORWARD", "status": "SUCCESS" }
    ]
  }
```

#### TC-MS-API-005：GET /saga/{sagaId} 不存在时返回 404

```
请求：GET http://localhost:8080/api/v1/saga/not-exist
期望结果：HTTP 404，ResourceNotFoundException
```

#### TC-MS-API-006：请求体缺少必填字段返回 400

```
请求：POST /api/v1/sprints/1/close-and-carry-over，Body={}
      （nextSprintId 和 newSprintName 均为空）
期望结果：HTTP 400，字段校验错误详情
```

#### TC-MS-API-007：task-service 内部迁移接口不对外暴露（路由隔离）

```
请求：POST http://localhost:8080/api/v1/tasks/internal/sprint-migrate
期望结果：HTTP 404 或 403（Gateway 不路由 /internal/** 路径）
验证：api-gateway 路由配置不包含 /internal/ 前缀
```

---

## 十、缓存测试（Redis）

#### TC-MS-CACHE-001：completeSprint 后 Redis 缓存失效

```
前置条件：Redis 中存在 sprints::1（ACTIVE）
测试步骤：调用 SprintService.completeSprint(1L)
期望结果：
  - Redis 中 sprints::1 被驱逐（@CacheEvict allEntries=true）
  - 再次 GET /sprints/1 触发数据库查询，返回 COMPLETED
```

#### TC-MS-CACHE-002：补偿回滚 Sprint 后缓存手动清除

```
前置条件：Redis 中存在 sprints::1（COMPLETED，Saga 中途写入）
测试步骤：执行 Step2 补偿（回滚为 ACTIVE）
期望结果：
  - Redis 中 sprints::1 被清除
  - GET /sprints/1 返回 ACTIVE
```

#### TC-MS-CACHE-003：burndown 缓存在 recordDailyPoint 后失效

```
前置条件：Redis 中存在 burndown::1
测试步骤：调用 BurndownService.recordDailyPoint(1L)
期望结果：burndown::1 缓存被驱逐，下次查询重新从 DB 读取
```

#### TC-MS-CACHE-004：getActiveSprint 缓存在 Sprint 关闭后返回 null

```
前置条件：Redis 中 sprints::active:1 = Sprint#1（ACTIVE）
测试步骤：执行 completeSprint(1L)，缓存失效后再次调用 getActiveSprint(1L)
期望结果：返回 null（无活跃 Sprint），不返回旧的缓存值
```

---

## 十一、测试数据 SQL

```sql
-- 清理（每个测试用例前执行）
DELETE FROM ms_project.saga_step_logs;
DELETE FROM ms_project.saga_instances;
DELETE FROM ms_burndown.burndown_points WHERE sprint_id IN (1, 2);
UPDATE ms_task.tasks SET sprint_id=1, original_sprint_id=NULL WHERE project_id=1;
UPDATE ms_project.sprints SET status='ACTIVE', completed_at=NULL WHERE id=1;
DELETE FROM ms_project.sprints WHERE id > 1 AND project_id=1;

-- 基础数据
INSERT INTO ms_project.sprints (id, project_id, name, status, start_date, end_date)
VALUES (1, 1, 'Sprint 1', 'ACTIVE', '2026-03-17', '2026-03-28')
ON CONFLICT (id) DO UPDATE SET status='ACTIVE', completed_at=NULL;

INSERT INTO ms_task.tasks (task_key, project_id, sprint_id, title, status, story_points)
VALUES
  ('TP-T1', 1, 1, '未完成任务1', 'TODO',        5.0),
  ('TP-T2', 1, 1, '进行中任务2', 'IN_PROGRESS',  3.0),
  ('TP-T3', 1, 1, '已完成任务3', 'DONE',          8.0)
ON CONFLICT (task_key) DO UPDATE
  SET sprint_id=1, status=EXCLUDED.status, original_sprint_id=NULL;
```

---

## 十二、测试类文件结构

```
burndown-backend-ms/
├── project-service/src/test/
│   └── java/com/burndown/project/
│       ├── saga/
│       │   ├── steps/
│       │   │   ├── ValidateAndLockStepTest.java
│       │   │   ├── CompleteCurrentSprintStepTest.java
│       │   │   ├── CreateNextSprintStepTest.java
│       │   │   ├── MigrateTasksStepTest.java
│       │   │   └── InitBurndownBaselineStepTest.java
│       │   └── SprintCloseSagaOrchestratorTest.java
│       ├── controller/
│       │   ├── SprintCloseControllerTest.java    // @WebMvcTest
│       │   └── SagaControllerTest.java           // @WebMvcTest
│       └── integration/
│           └── SprintCloseSagaIntegrationTest.java  // @SpringBootTest + WireMock
├── task-service/src/test/
│   └── java/com/burndown/task/
│       ├── service/
│       │   └── TaskMigrateServiceTest.java
│       └── integration/
│           └── TaskMigrateIntegrationTest.java
└── burndown-service/src/test/
    └── java/com/burndown/burndown/
        ├── service/
        │   └── BurndownDeleteServiceTest.java
        └── consumer/
            └── TaskEventConsumerTest.java
```

---

## 十三、测试执行检查清单

```
[ ] SagaStep 单元测试：5 步骤类均通过（Mockito）
[ ] 编排器单元测试：补偿顺序正确，日志独立事务写入
[ ] task-service 迁移方法：只迁移非 DONE，幂等回迁
[ ] burndown-service deleteSprintPoints：幂等
[ ] Feign Fallback：迁移接口不可静默降级，必须抛异常让 Saga 感知
[ ] Resilience4j 熔断：失败率超阈值后触发 OPEN 状态
[ ] 集成正向：三个 Schema 数据最终一致
[ ] 集成补偿：Step5 失败后任务回迁、Sprint 恢复、燃尽点删除
[ ] 幂等：同 Sprint 重复提交返回 409，不产生重复数据
[ ] Gateway：/internal/** 接口不对外暴露
[ ] Redis：Sprint 关闭后 active 缓存失效
[ ] RabbitMQ：Saga 完成后 MQ 事件链路正常（task 事件触发 burndown 更新）
[ ] saga_instances 唯一部分索引：并发提交只有一个成功
```

持续监控，一步步显示。



