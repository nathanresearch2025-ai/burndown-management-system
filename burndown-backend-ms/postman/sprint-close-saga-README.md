# Sprint 关闭与任务结转 Saga — 接口说明与测试指南

## 1. 改造概述

本次改造基于 `burndown-backend-ms` 微服务代码，在不引入 Seata/消息编舞的前提下，通过**编排式 Saga（Orchestration Saga）** 实现 Sprint 关闭时的跨服务分布式一致性。

| 改造内容 | 涉及服务 |
|----------|----------|
| 新增 `saga_instances`、`saga_step_logs` 表 | project-service |
| 新增 `original_sprint_id` 字段 | task-service |
| 新增 `SagaInstance`、`SagaStepLog` 实体 | project-service |
| 新增 `TaskServiceClient`、`BurndownServiceClient` Feign | project-service |
| 新增 5 个 `SagaStep` 实现类 | project-service |
| 新增 `SprintCloseSagaOrchestrator` 编排器 | project-service |
| 新增 `/sprints/{id}/close-and-carry-over` 接口 | project-service |
| 新增 `/sagas/{sagaId}` 查询接口 | project-service |
| 新增批量任务迁移/补偿方法及内部接口 | task-service |
| 新增燃尽点删除方法及内部接口 | burndown-service |

---

## 2. Saga 执行流程

```
POST /api/v1/sprints/{id}/close-and-carry-over
        │
        ▼
┌─────────────────────────────┐
│ Step 1: ValidateAndLockStep │  校验 Sprint=ACTIVE，无重复 Saga
└────────────┬────────────────┘
             │ 成功
        ▼
┌────────────────────────────────────┐
│ Step 2: CompleteCurrentSprintStep  │  Sprint → COMPLETED
└────────────┬───────────────────────┘
             │ 成功
        ▼
┌──────────────────────────┐
│ Step 3: CreateNextSprint │  创建下一个 PLANNED Sprint
└────────────┬─────────────┘
             │ 成功
        ▼
┌─────────────────────────┐
│ Step 4: MigrateTasks    │  调用 task-service 迁移未完成任务
└────────────┬────────────┘
             │ 成功
        ▼
┌─────────────────────────┐
│ Step 5: InitBurndown    │  调用 burndown-service 初始化燃尽基线
└────────────┬────────────┘
             │ 成功
        ▼
    Saga = SUCCEEDED

任意步骤失败 → 逆序补偿 → Saga = COMPENSATED
```

---

## 3. 新增 API 接口

### 3.1 关闭 Sprint 并结转（主入口）

```
POST /api/v1/sprints/{sprintId}/close-and-carry-over
Authorization: Bearer <token>
Content-Type: application/json

{
  "nextSprintName": "Sprint 2"   // 可选，默认为 "<原名> (Next)"
}
```

**响应（成功）：**
```json
{
  "success": true,
  "data": {
    "id": "uuid-saga-id",
    "sagaType": "SPRINT_CLOSE",
    "status": "SUCCEEDED",
    "sprintId": 1,
    "nextSprintId": 2,
    "createdAt": "2026-03-29T10:00:00"
  }
}
```

**响应（失败/补偿后）：**
```json
{
  "success": true,
  "data": {
    "id": "uuid-saga-id",
    "status": "COMPENSATED",
    "currentStep": "MIGRATE_TASKS"
  }
}
```
HTTP 状态码：成功=200，失败=500

---

### 3.2 查询 Saga 状态（可轮询）

```
GET /api/v1/sagas/{sagaId}
Authorization: Bearer <token>
```

**响应：**
```json
{
  "success": true,
  "data": {
    "id": "uuid-saga-id",
    "status": "SUCCEEDED",
    "currentStep": "INIT_BURNDOWN",
    "sprintId": 1,
    "nextSprintId": 2,
    "steps": [
      { "stepName": "VALIDATE_AND_LOCK", "stepStatus": "SUCCESS", "executedAt": "..." },
      { "stepName": "COMPLETE_CURRENT_SPRINT", "stepStatus": "SUCCESS", "executedAt": "..." },
      { "stepName": "CREATE_NEXT_SPRINT", "stepStatus": "SUCCESS", "executedAt": "..." },
      { "stepName": "MIGRATE_TASKS", "stepStatus": "SUCCESS", "executedAt": "..." },
      { "stepName": "INIT_BURNDOWN", "stepStatus": "SUCCESS", "executedAt": "..." }
    ]
  }
}
```

---

### 3.3 内部接口（服务间调用，不对外暴露）

| 方法 | 路径 | 服务 | 说明 |
|------|------|------|------|
| POST | `/api/v1/internal/tasks/sprint/{sprintId}/migrate?targetSprintId=N` | task-service:8083 | 迁移未完成任务 |
| POST | `/api/v1/internal/tasks/sprint/{sprintId}/compensate` | task-service:8083 | 补偿回迁任务 |
| DELETE | `/api/v1/internal/burndown/sprint/{sprintId}` | burndown-service:8084 | 删除燃尽点 |

---

## 4. 数据库变更

### project-service (ms_project schema)

```sql
-- Saga 实例表
CREATE TABLE saga_instances (
    id             VARCHAR(36)  PRIMARY KEY,  -- UUID
    saga_type      VARCHAR(100) NOT NULL,      -- 'SPRINT_CLOSE'
    status         VARCHAR(20)  NOT NULL,      -- STARTED/IN_PROGRESS/SUCCEEDED/COMPENSATED/FAILED
    current_step   VARCHAR(100),
    sprint_id      BIGINT       NOT NULL,
    project_id     BIGINT       NOT NULL,
    next_sprint_id BIGINT,
    context_json   TEXT,
    failure_reason TEXT,
    created_at     TIMESTAMP    DEFAULT NOW(),
    updated_at     TIMESTAMP    DEFAULT NOW()
);

-- 唯一部分索引：同一 Sprint 不能同时有两个活跃 Saga
CREATE UNIQUE INDEX uidx_saga_sprint_active
    ON saga_instances (sprint_id)
    WHERE status IN ('STARTED', 'IN_PROGRESS');

-- Saga 步骤日志表
CREATE TABLE saga_step_logs (
    id          BIGSERIAL    PRIMARY KEY,
    saga_id     VARCHAR(36)  NOT NULL,
    step_name   VARCHAR(100) NOT NULL,
    step_status VARCHAR(20)  NOT NULL,  -- SUCCESS/FAILED
    error_msg   TEXT,
    executed_at TIMESTAMP    DEFAULT NOW()
);
```

### task-service (ms_task schema)

```sql
-- 新增字段，记录任务结转前所在 Sprint
ALTER TABLE ms_task.tasks ADD COLUMN original_sprint_id BIGINT;
```

---

## 5. 幂等保护

- 唯一部分索引 `uidx_saga_sprint_active` 保证同一 Sprint 同时只有一个活跃 Saga
- `ValidateAndLockStep` 在 execute 时检查 `saga_instances` 是否已有 `STARTED/IN_PROGRESS` 记录
- 重复提交同一 Sprint 的关闭请求将抛出 `BusinessException(SAGA_ALREADY_RUNNING, 409)`

---

## 6. 补偿逻辑

| 步骤 | 补偿动作 |
|------|----------|
| ValidateAndLockStep | 无操作（无状态变更） |
| CompleteCurrentSprintStep | 将 Sprint 状态回滚为 ACTIVE，清除 completedAt |
| CreateNextSprintStep | 删除已创建的新 Sprint |
| MigrateTasksStep | 调用 task-service 补偿接口，将任务回迁到原 Sprint |
| InitBurndownStep | 调用 burndown-service 删除新 Sprint 的燃尽点 |

---

## 7. Postman 测试步骤

导入 `sprint-close-saga.postman_collection.json`，按以下顺序执行：

```
1. Auth → Login                      # 获取 JWT token
2. 准备测试数据 → 2.1 创建项目         # 自动保存 project_id
3. 准备测试数据 → 2.2 创建Sprint       # 自动保存 sprint_id
4. 准备测试数据 → 2.3 启动Sprint
5. 准备测试数据 → 2.4 创建未完成任务
6. 准备测试数据 → 2.5 创建已完成任务
7. Saga → 3.1 执行 Close & Carry Over  # 自动保存 saga_id，观察状态
8. Saga → 3.2 查询 Saga 状态           # 查看逐步执行记录
9. Saga → 3.3 幂等测试                 # 应返回错误
10. 验证结果 → 4.1 ~ 4.4              # 验证数据一致性
```

### 预期结果

| 验证项 | 期望值 |
|--------|--------|
| 原 Sprint 状态 | COMPLETED |
| 新 Sprint 存在 | 是，状态 PLANNED |
| 未完成任务 sprintId | 新 Sprint ID |
| 已完成任务 sprintId | 原 Sprint ID |
| 燃尽图点位 | 原/新 Sprint 各有记录 |
| Saga status | SUCCEEDED |
| 重复提交 | 报错，无重复数据 |

---

## 8. 文件清单

### 新增文件

```
burndown-backend-ms/
├── sql/
│   ├── init-project.sql          # 新增 saga_instances, saga_step_logs 表
│   └── init-task.sql             # 新增 original_sprint_id 字段
├── project-service/src/main/java/com/burndown/project/
│   ├── client/
│   │   ├── TaskServiceClient.java
│   │   ├── TaskServiceClientFallback.java
│   │   ├── BurndownServiceClient.java
│   │   └── BurndownServiceClientFallback.java
│   ├── dto/
│   │   ├── SprintCloseRequest.java
│   │   └── SagaInstanceDTO.java
│   ├── entity/
│   │   ├── SagaInstance.java
│   │   └── SagaStepLog.java
│   ├── repository/
│   │   ├── SagaInstanceRepository.java
│   │   └── SagaStepLogRepository.java
│   ├── saga/
│   │   ├── SagaContext.java
│   │   ├── SagaStep.java
│   │   ├── SprintCloseSagaOrchestrator.java
│   │   └── step/
│   │       ├── ValidateAndLockStep.java
│   │       ├── CompleteCurrentSprintStep.java
│   │       ├── CreateNextSprintStep.java
│   │       ├── MigrateTasksStep.java
│   │       └── InitBurndownStep.java
│   └── controller/
│       └── SagaController.java
├── task-service/src/main/java/com/burndown/task/
│   └── controller/
│       └── TaskInternalController.java
├── burndown-service/src/main/java/com/burndown/burndown/
│   └── controller/
│       └── BurndownInternalController.java
└── postman/
    ├── sprint-close-saga.postman_collection.json
    └── sprint-close-saga-README.md   (本文件)
```

### 修改文件

```
project-service:
  - controller/SprintController.java   # 新增 close-and-carry-over 端点

task-service:
  - entity/Task.java                   # 新增 originalSprintId 字段
  - repository/TaskRepository.java     # 新增 findBySprintIdAndOriginalSprintId
  - service/TaskService.java           # 新增 migrateUndoneTasks, compensateMigratedTasks

burndown-service:
  - repository/BurndownPointRepository.java  # 新增 deleteBySprintId
  - service/BurndownService.java             # 新增 deleteSprintPoints
```
