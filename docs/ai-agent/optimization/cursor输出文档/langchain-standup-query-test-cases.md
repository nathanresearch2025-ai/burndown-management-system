# `POST /agent/langchain/standup/query` 测试案例

## 1. 测试范围

覆盖以下链路与组件：

- Spring Boot：`POST /api/v1/agent/langchain/standup/query`
- Python FastAPI：`POST /agent/standup/query`
- Spring 工具端点：`POST /api/v1/agent/tools/*`
- 关键非功能：性能（P95/P99）、可观测（traceId/分段耗时/指标）、降级/超时、并发与稳定性

## 2. 前置条件与通用准备

- 环境：
  - Spring Boot 正常启动（`/api/v1/actuator/health` 为 UP）
  - Python 服务正常启动（`/agent/standup/query` 可访问）
  - LLM API Key 可用（DeepSeek/OpenAI 兼容）
  - 数据库有可用测试数据（至少一个 project、一个 user、一个 sprint；并有 IN_PROGRESS 任务与 burndown_points）
- 统一约定：
  - 测试请求需带 `traceId`（Spring 会自动生成并写入 body；若支持 header，记录 `X-Trace-Id`）
  - 记录每次调用的：HTTP 状态码、响应体、总耗时（客户端）、服务端日志片段（含 traceId）
- 样例请求（可按环境调整）：

```json
{
  "question": "我今天有哪些进行中的任务？另外当前 Sprint 的燃尽是否偏离计划，有什么风险？",
  "projectId": 1,
  "sprintId": 1,
  "userId": 1,
  "timezone": "Asia/Shanghai",
  "traceId": "manual-test-001"
}
```

## 3. 功能正确性（Functional）

### F-01 复合问题（任务 + 燃尽 + 风险）

- **目的**：验证复合意图可正常返回结论、工具列表与证据。
- **输入**：同时询问任务与燃尽风险的 question；projectId/sprintId/userId 均有效。
- **步骤**：
  - 调用 `POST /api/v1/agent/langchain/standup/query`
- **期望**：
  - HTTP 200（或统一包装的 OK）
  - `data.answer` 非空
  - `data.toolsUsed` 包含（至少）`getInProgressTasks/getSprintBurndown/evaluateBurndownRisk`（顺序可不强制）
  - `data.evidence` 非空（至少包含任务 key 或 planned/actual 等数值）
  - `data.riskLevel` 为 `LOW|MEDIUM|HIGH|UNKNOWN` 之一（若当前实现不填则允许为空，但需在技术方案中明确）

### F-02 仅任务问题

- **目的**：只问任务时输出聚焦任务，且不强制燃尽/风险内容。
- **输入**：`question="我今天有哪些进行中的任务？"`
- **期望**：
  - `answer` 提及任务列表/数量
  - `toolsUsed` 至少包含 `getInProgressTasks`
  - `evidence` 含任务 key 或标题片段

### F-03 仅燃尽/风险问题

- **输入**：`question="Sprint 1 当前燃尽是否偏离计划？风险等级是多少？"`
- **期望**：
  - `toolsUsed` 至少包含 `getSprintBurndown`，并在需要时包含 `evaluateBurndownRisk`
  - `answer` 包含 planned/actual/偏差 或 风险等级解释

### F-04 sprintId 缺失

- **目的**：验证 sprintId 为空时行为与提示清晰。
- **输入**：不传 `sprintId` 或为 null。
- **期望（二选一，需固定并验收）**：
  - A：正常返回但提示“未提供 sprintId，无法分析燃尽/风险”，仍可返回任务结果
  - B：返回参数校验错误（HTTP 400），错误信息清晰

### F-05 无进行中任务

- **前置**：该 user 在该 project 下没有 IN_PROGRESS 任务。
- **期望**：
  - `answer` 明确提示“当前没有进行中的任务”
  - 不出现编造任务 key

### F-06 burndown 数据缺失

- **前置**：该 sprint 没有 burndown_points。
- **期望**：
  - `answer` 提示“暂无燃尽图数据，无法评估风险”（或同义表达）
  - 仍可返回任务数据（若 question 涉及任务）

## 4. 工具端点与数据一致性（Tool/Data）

### T-01 工具端点可用性（单独验证）

- **步骤**：分别调用：
  - `/api/v1/agent/tools/in-progress-tasks`
  - `/api/v1/agent/tools/sprint-burndown`
  - `/api/v1/agent/tools/burndown-risk`
- **期望**：
  - 返回 200
  - 返回体格式与 Python 侧预期一致（字符串可解析/可读）

### T-02 `getInProgressTasks` 查询下推验证（优化后）

- **目的**：验证不再“findByProjectId 后 Java 过滤”的全量扫描路径。
- **方法**：
  - 开启 SQL 日志或使用 APM/DB 慢查询统计（择一）
  - 调用复合问题 20 次
- **期望**：
  - 数据库侧出现带 `projectId + assigneeId + status` 的过滤查询
  - Spring 应用内存与 CPU 峰值下降（对比优化前基线）

## 5. 可观测性（Observability）

### O-01 traceId 贯穿（Spring→Python→工具）

- **步骤**：
  - 发起一次请求，指定 `traceId=manual-test-xyz`
  - 检索 Spring 日志与 Python 日志
- **期望**：
  - Spring 调 Python 的日志包含 traceId
  - Python 入口日志包含 traceId
  - Python 调工具端点的日志/请求头（若实现）包含相同 traceId

### O-02 分段耗时日志（Python）

- **前置**：开启 `LOG_STEP_TIMING=true`（或对应开关）
- **期望**：同一 traceId 下出现至少以下字段（名称允许略有差异，但要能对应）：
  - `tools_ms_total`
  - `llm_summarize_ms`
  - `total_ms`

### O-03 指标采集（Spring）

- **前置**：Prometheus 指标可访问：`/api/v1/actuator/prometheus`
- **步骤**：请求 10 次后查询指标。
- **期望**：
  - `langchain_client_duration_ms`（或等价 timer）有样本
  - 错误计数器（若有）维度正确（timeout/5xx/io）

## 6. 异常、超时与降级（Resilience）

### R-01 Python 服务不可用

- **方法**：停止 Python 服务或把 `langchain.base-url` 指向无效地址。
- **期望**：
  - Spring 返回 `502/504`（或统一包装错误）
  - 错误信息不泄露内部细节（不包含堆栈/敏感配置）
  - Micrometer 错误计数增加（如实现）

### R-02 LLM 超时/限流

- **方法**：将 LLM base-url 指向故障代理、或临时降低 LLM 超时时间触发 timeout。
- **期望**：
  - 在 `langchain.timeout`（建议 30s）内返回
  - 返回“服务繁忙/请稍后重试”的降级信息（或明确失败）
  - 不出现请求线程长时间阻塞（观察线程池/响应耗时上限）

### R-03 单个工具端点超时

- **方法**：人为让某个工具端点变慢（例如 DB 限速/断开连接/注入 sleep，仅测试环境）。
- **期望（并发工具方案）**：
  - 整体耗时接近“最慢工具 + 汇总 LLM”，而不是 3 个工具累加
  - 输出中对缺失工具数据有说明（例如 evidence 提示该工具失败）

### R-04 工具返回 5xx/4xx

- **期望**：
  - Python 侧捕获并记录 toolName + 错误原因
  - 最终回答不编造数据，明确说明“部分数据获取失败”

## 7. 性能与对比（Performance）

### P-01 基线测量（优化前）

- **目的**：形成可对比基线。
- **方法**：
  - 固定输入（同 projectId/sprintId/userId/question）
  - 连续调用 N=50（或 100）
  - 记录平均、P50、P95、P99、错误率
- **输出**：
  - 基线报告（表格/JSON）保存到测试记录（可附在 issue/文档）

### P-02 优化后测量与验收

- **通过标准**：
  - **P95 < 8s**
  - **P99 < 15s**
  - 错误率 < 1%（非故障注入）

### P-03 并发用户压测（稳定性）

- **方法**：
  - 并发 5/10/20 用户（逐级）
  - 每用户 10 次请求
- **期望**：
  - 无明显错误率飙升
  - 无内存持续增长（泄露）
  - Python 连接池未耗尽（无大量 connect timeout）

## 8. 缓存（如启用）的验证

### C-01 工具缓存命中（短 TTL）

- **前置**：开启工具缓存（Spring Redis/Caffeine 或 Python LRU）
- **步骤**：
  - 在 TTL 窗口内对相同 `(projectId,sprintId,userId)` 连续请求 5 次
- **期望**：
  - 工具侧耗时显著下降（日志/指标体现）
  - DB 查询次数下降（通过 DB 监控或日志抽样）

### C-02 TTL 过期后更新

- **步骤**：
  - 等待 TTL 过期后再次请求
- **期望**：
  - 缓存 miss 后重新查询并更新缓存

## 9. 配置开关与回滚验证

### S-01 Pipeline 模式切换（fast ↔ legacy）

- **前置**：支持 `STANDUP_PIPELINE_MODE=fast|legacy`
- **步骤**：
  - fast 模式跑 F-01
  - legacy 模式跑 F-01
- **期望**：
  - 两种模式都能正确返回（允许回答措辞不同，但不得缺关键字段/不得编造数据）
  - 切换无需改代码（仅配置/环境变量）

### S-02 工具并发开关（true ↔ false）

- **期望**：
  - 关闭并发时，tools_ms_total 呈现“累加更明显”
  - 开启并发时，tools_ms_total 接近 max(tool_i)

## 10. 测试记录模板（建议）

每条用例至少记录：

- traceId
- 请求体（脱敏）
- 响应体（脱敏）
- 客户端耗时
- 服务端分段耗时（如有）
- 结论：Pass/Fail + 失败原因

