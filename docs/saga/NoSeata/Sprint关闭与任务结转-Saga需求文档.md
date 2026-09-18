# Sprint关闭与任务结转 Saga 需求文档

## 1. 文档目标

基于当前微服务代码结构，设计一个适合引入 Saga 分布式事务的真实业务场景，用于解决 Sprint 关闭时跨服务数据一致性问题。

本文档对应的现有服务边界如下：

- `project-service`：负责项目、Sprint 的创建、启动、完成
- `task-service`：负责任务创建、任务状态变更、任务 Sprint 归属
- `burndown-service`：负责燃尽图快照记录与燃尽数据查询

当前代码中已经存在如下跨服务依赖：

- `task-service` 通过 Feign 调用 `project-service` 校验 Sprint 是否存在
- `task-service` 通过 RabbitMQ 发布任务事件
- `burndown-service` 通过消费任务事件和主动调用 `task-service` / `project-service` 生成燃尽图数据

这说明系统已经具备跨服务协作基础，但尚未具备完整的跨服务事务编排与补偿能力。

## 2. 场景选型

### 2.1 需求名称

Sprint 关闭并自动结转未完成任务到下一个 Sprint

### 2.2 业务背景

Scrum 项目中，项目经理或 Scrum Master 在 Sprint 结束时，通常需要执行以下动作：

1. 关闭当前 Sprint
2. 统计本期完成情况
3. 将未完成任务自动结转到下一个 Sprint
4. 为下一个 Sprint 初始化燃尽图基线
5. 保证用户在页面上看到的数据始终一致

在单体应用中，这些步骤通常可以放在一个本地事务中完成；但在当前微服务架构下，这些数据分散在不同服务和不同数据库中：

- Sprint 状态在 `ms_project.sprints`
- 任务归属在 `ms_task.tasks`
- 燃尽图点位在 `ms_burndown.burndown_points`

因此，该场景天然适合使用 Saga 分布式事务。

### 2.3 为什么这个场景比普通“创建任务”更适合 Saga

- 涉及至少 3 个独立微服务，且每个服务有独立持久化
- 每一步业务动作都已经有明确的服务归属
- 中途失败会产生明显的业务脏数据
- 失败后存在清晰的补偿路径
- 用户对结果一致性要求高，不能接受“旧 Sprint 已完成，但任务还在旧 Sprint，燃尽图也没初始化”的状态

## 3. 当前系统中的问题

### 3.1 当前实现能力

现有代码已经支持：

- `project-service`
  - 创建 Sprint
  - 启动 Sprint
  - 完成 Sprint
- `task-service`
  - 按 Sprint 查询任务
  - 更新任务 `sprintId`
  - 更新任务状态
- `burndown-service`
  - 手动记录某个 Sprint 的每日燃尽点
  - 消费任务事件后刷新燃尽数据

### 3.2 当前实现缺陷

如果直接由前端顺序调用多个服务完成 Sprint 收尾，会出现以下问题：

1. 当前 Sprint 已被置为 `COMPLETED`，但未完成任务尚未迁移
2. 下一个 Sprint 已创建，但任务迁移部分成功、部分失败
3. 任务已经迁移成功，但新 Sprint 的燃尽图基线没有初始化
4. 任务事件发布失败时当前代码不会回滚主事务，导致跨服务状态不可追溯
5. 用户重复点击“关闭 Sprint”可能导致重复创建 Sprint 或重复迁移任务

因此需要一个具备编排、幂等、重试、补偿能力的 Saga 流程。

## 4. 需求概述

### 4.1 核心目标

新增“关闭 Sprint 并自动结转”能力，要求：

- 以一个业务操作完成 Sprint 收尾
- 跨 `project-service`、`task-service`、`burndown-service` 保持最终一致
- 任一步骤失败后能够自动补偿
- 整个流程可查询、可重试、可审计

### 4.2 目标用户

- 项目经理
- Scrum Master
- 具有 Sprint 管理权限的项目管理员

### 4.3 适用项目

- `project.type = SCRUM` 的项目

## 5. 业务规则

### 5.1 触发条件

用户在当前 `ACTIVE` 状态的 Sprint 上点击“关闭 Sprint 并结转未完成任务”。

### 5.2 前置校验

发起关闭前必须满足：

1. 当前 Sprint 状态为 `ACTIVE`
2. 当前项目下不存在其他正在执行的 Sprint 关闭 Saga
3. 操作人具有 Sprint 管理权限
4. 当前 Sprint 归属项目有效
5. 若选择“自动创建并启动下一 Sprint”，则下一 Sprint 的名称、日期范围、容量信息必须完整

### 5.3 任务结转规则

- `DONE` 状态任务留在原 Sprint，不迁移
- `TODO`、`IN_PROGRESS`、`IN_REVIEW` 状态任务迁移到新 Sprint
- 迁移后保留原任务 ID、任务 key、负责人、优先级、故事点
- 迁移后不清空已有工时日志
- 迁移后任务状态保持原状态，不自动改回 `TODO`

### 5.4 Sprint 关闭规则

- 原 Sprint 关闭后状态变为 `COMPLETED`
- 原 Sprint 应记录完成时间
- 新 Sprint 可由用户选择：
  - 仅创建为 `PLANNED`
  - 创建后立即启动为 `ACTIVE`

### 5.5 燃尽图规则

- 原 Sprint 在关闭时补记一次最终燃尽点
- 新 Sprint 在结转完成后写入第一条基线燃尽点
- 新 Sprint 基线点的 `totalPoints`、`remainingPoints` 应等于迁移后未完成任务总故事点

## 6. Saga 业务流程

### 6.1 Saga 参与服务

- Saga Orchestrator：建议新增独立编排模块，或由 `project-service` 承担编排职责
- `project-service`
- `task-service`
- `burndown-service`

### 6.2 正向事务步骤

#### Step 1：创建 Saga 实例

- 生成全局 `sagaId`
- 记录操作人、项目 ID、原 Sprint ID、目标 Sprint 参数、执行模式
- Saga 状态置为 `RUNNING`

#### Step 2：锁定原 Sprint

- `project-service` 将原 Sprint 标记为“关闭中”
- 关闭中状态下，禁止再次发起关闭、禁止修改 Sprint 关键属性

说明：现有 `sprints.status` 只有 `PLANNED`、`ACTIVE`、`COMPLETED`，本需求建议增加临时业务状态 `CLOSING`

#### Step 3：快照未完成任务

- `task-service` 查询原 Sprint 下所有未完成任务
- 记录待迁移任务清单和原 `sprintId`
- 快照结果写入 Saga 明细表，作为补偿依据

#### Step 4：创建下一 Sprint

- `project-service` 创建下一 Sprint
- 默认状态为 `PLANNED`
- 若用户选择“关闭后立即开始下一 Sprint”，则先创建再进入后续启动步骤

#### Step 5：批量迁移未完成任务

- `task-service` 将未完成任务的 `sprintId` 从原 Sprint 批量改为新 Sprint
- 记录迁移成功任务数、失败任务数、失败原因
- 该步骤必须支持幂等重试

#### Step 6：关闭原 Sprint

- `project-service` 将原 Sprint 状态从 `CLOSING` 改为 `COMPLETED`
- 写入 `completedAt`

#### Step 7：按需启动新 Sprint

- 若用户选择“立即开始下一 Sprint”，则将新 Sprint 状态改为 `ACTIVE`
- 写入 `startedAt`

#### Step 8：写入燃尽图点位

- `burndown-service` 为原 Sprint 记录最终燃尽点
- `burndown-service` 为新 Sprint 记录初始化基线点

#### Step 9：Saga 完成

- Saga 状态改为 `SUCCEEDED`
- 返回新 Sprint 信息、迁移任务统计、燃尽图初始化结果

## 7. 补偿事务设计

### 7.1 需要补偿的失败场景

- 新 Sprint 创建成功，但任务迁移失败
- 任务迁移成功，但原 Sprint 关闭失败
- 原 Sprint 已关闭，但新 Sprint 启动失败
- 任务迁移和 Sprint 状态都成功，但燃尽图初始化失败

### 7.2 补偿原则

- 补偿顺序与正向流程相反
- 补偿动作必须幂等
- 补偿失败必须可继续人工重试

### 7.3 补偿动作定义

#### 补偿 A：回滚燃尽图初始化

- 删除或覆盖本次 Saga 生成的新 Sprint 基线点
- 如原 Sprint 最终点是本次新写入，也需要按日志撤销

#### 补偿 B：回滚新 Sprint 启动

- 若新 Sprint 已启动，则回退为 `PLANNED`
- 清除 `startedAt`

#### 补偿 C：回滚原 Sprint 完成状态

- 将原 Sprint 从 `COMPLETED` 恢复为 `ACTIVE`
- 清除 `completedAt`

#### 补偿 D：回滚任务迁移

- 将已迁移任务的 `sprintId` 从新 Sprint 改回原 Sprint
- 按任务快照逐条恢复，避免批量回滚时误伤新产生的数据

#### 补偿 E：回滚新 Sprint 创建

- 若新 Sprint 未被其他流程使用，则删除该 Sprint
- 若业务不允许物理删除，则标记为 `CANCELLED`

说明：当前代码未定义 `CANCELLED` 状态，本需求允许在设计阶段新增

## 8. 状态机要求

### 8.1 Saga 状态

- `PENDING`
- `RUNNING`
- `COMPENSATING`
- `SUCCEEDED`
- `FAILED`
- `COMPENSATED`

### 8.2 Sprint 状态扩展建议

现有状态：

- `PLANNED`
- `ACTIVE`
- `COMPLETED`

建议新增：

- `CLOSING`：Saga 正在关闭当前 Sprint
- `CLOSE_FAILED`：Saga 执行失败且待人工处理
- `CANCELLED`：已创建但被补偿撤销的新 Sprint

## 9. 接口需求

### 9.1 编排入口接口

建议新增统一入口：

`POST /api/v1/sprint-closure-sagas`

请求示例字段：

- `projectId`
- `sourceSprintId`
- `createNextSprint`
- `startNextSprint`
- `nextSprint.name`
- `nextSprint.goal`
- `nextSprint.startDate`
- `nextSprint.endDate`
- `nextSprint.totalCapacity`

返回字段：

- `sagaId`
- `status`
- `sourceSprintId`
- `targetSprintId`
- `movedTaskCount`
- `message`

### 9.2 Saga 查询接口

建议新增：

- `GET /api/v1/sprint-closure-sagas/{sagaId}`
- `POST /api/v1/sprint-closure-sagas/{sagaId}/retry`
- `POST /api/v1/sprint-closure-sagas/{sagaId}/compensate`

### 9.3 服务侧能力补充

为支持该 Saga，现有服务需要补充以下内部或外部接口：

`project-service`

- 锁定 Sprint
- 解锁 / 回滚 Sprint 状态
- 创建下一 Sprint
- 启动下一 Sprint
- 删除或取消下一 Sprint

`task-service`

- 查询未完成任务清单
- 批量迁移任务到指定 Sprint
- 根据任务快照回滚迁移

`burndown-service`

- 记录指定 Sprint 的最终燃尽点
- 记录指定 Sprint 的初始化基线点
- 撤销指定 Saga 写入的燃尽点

## 10. 数据与审计要求

### 10.1 Saga 主表

建议新增 `sprint_closure_saga` 表，至少包含：

- `saga_id`
- `project_id`
- `source_sprint_id`
- `target_sprint_id`
- `status`
- `operator_id`
- `failure_reason`
- `created_at`
- `updated_at`

### 10.2 Saga 步骤表

建议新增 `sprint_closure_saga_step` 表，记录：

- `saga_id`
- `step_name`
- `step_status`
- `retry_count`
- `request_payload`
- `response_payload`
- `error_message`
- `started_at`
- `finished_at`

### 10.3 任务快照表

建议新增任务迁移快照，用于补偿：

- `saga_id`
- `task_id`
- `original_sprint_id`
- `target_sprint_id`
- `original_status`
- `snapshot_time`

## 11. 幂等与并发控制要求

### 11.1 幂等要求

- 同一 `sourceSprintId` 在 `RUNNING` 或 `COMPENSATING` 状态下不能重复发起
- 使用 `businessKey = projectId + sourceSprintId + closeOperationType` 做唯一约束
- 服务侧每个步骤都必须支持重复调用不产生重复副作用

### 11.2 并发控制

- 同一项目同一时刻只允许一个 Sprint 关闭 Saga 运行
- 同一任务在迁移期间禁止被其他流程再次迁移
- 前端按钮在 Saga 执行期间必须禁用，并展示“关闭中”

## 12. 异常处理要求

系统必须区分以下几类异常：

- 可重试异常：网络抖动、消息投递失败、短暂服务不可用
- 不可重试异常：Sprint 状态非法、目标 Sprint 参数无效、权限不足
- 需人工介入异常：补偿失败、任务回滚部分成功、目标 Sprint 已被其他操作使用

对于可重试异常，系统应自动重试；对于不可重试异常，应立即终止并触发补偿；对于人工介入异常，应将 Saga 标记为 `FAILED` 或 `CLOSE_FAILED` 并告警。

## 13. 非功能需求

### 13.1 可观测性

- 每个 Saga 必须生成统一 `traceId` / `sagaId`
- 全链路日志必须能按 `sagaId` 检索
- 需要暴露 Saga 成功率、平均耗时、补偿次数、失败次数指标

### 13.2 性能

- 关闭 1 个包含 200 条未完成任务的 Sprint，系统应在 10 秒内完成主要业务流程
- 批量迁移任务时不得逐条远程调用，应采用批处理

### 13.3 可恢复性

- 系统重启后应能恢复未完成 Saga
- 补偿中的 Saga 不能因服务重启而丢失上下文

## 14. 验收标准

### 14.1 成功场景

当用户关闭一个 `ACTIVE` Sprint 且选择自动创建下一 Sprint 时：

1. 原 Sprint 最终变为 `COMPLETED`
2. 新 Sprint 被成功创建，且按配置为 `PLANNED` 或 `ACTIVE`
3. 所有未完成任务都迁移到新 Sprint
4. 已完成任务仍保留在原 Sprint
5. 原 Sprint 与新 Sprint 的燃尽图点位均已正确生成
6. Saga 状态为 `SUCCEEDED`

### 14.2 失败补偿场景

当任务迁移完成后，燃尽图初始化失败时：

1. 系统自动触发补偿
2. 已迁移任务被恢复到原 Sprint
3. 原 Sprint 恢复为 `ACTIVE`
4. 新 Sprint 被删除或标记取消
5. Saga 状态最终为 `COMPENSATED` 或 `FAILED`
6. 日志中能清晰看到失败步骤与补偿步骤

### 14.3 幂等场景

重复提交同一关闭请求时：

1. 系统不得重复创建下一 Sprint
2. 系统不得重复迁移任务
3. 前端应得到同一 `sagaId` 或明确的“流程执行中”提示

## 15. 实施建议

### 15.1 推荐实现方式

优先采用编排式 Saga，而不是纯事件编舞，原因如下：

- 当前业务存在明确入口“关闭 Sprint”
- 需要顺序执行多个强业务依赖步骤
- 失败补偿逻辑明确，适合中心化编排
- 当前系统已经有 Feign 与消息混合架构，增加编排层成本可控

### 15.2 与现有代码的契合点

- `project-service` 已具备 Sprint 创建、启动、完成能力，可扩展为 Saga 的核心状态服务
- `task-service` 已具备按 Sprint 查询和更新任务的能力，可扩展批量迁移接口
- `burndown-service` 已具备按 Sprint 记录燃尽点能力，可扩展“初始化基线”和“撤销点位”能力

## 16. 总结

“Sprint 关闭并自动结转未完成任务到下一个 Sprint”是当前项目中最合理的 Saga 分布式事务落地场景，因为它：

- 完全贴合现有微服务边界
- 涉及真实的跨服务一致性问题
- 具备清晰的业务价值
- 拥有明确的正向事务与补偿事务
- 可直接作为后续技术设计和接口设计的输入

后续若进入设计阶段，可继续输出：

- Saga 时序图
- 服务接口定义文档
- 状态机设计文档
- 数据库表设计文档
- 异常补偿时序图
