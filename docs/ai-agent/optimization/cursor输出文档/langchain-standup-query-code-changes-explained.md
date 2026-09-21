# 代码更新说明：为什么这样改（对应技术实现方案）

本文档结合本次实际代码修改，解释每个改动点解决了什么性能问题，以及如何验证其效果。

## 1. Spring：`getInProgressTasks` 查询下推到数据库

### 1.1 改动文件

- `backend/src/main/java/com/burndown/repository/TaskRepository.java`
- `backend/src/main/java/com/burndown/aiagent/standup/tool/StandupTaskTools.java`

### 1.2 具体改动

- 在 `TaskRepository` 新增了精准查询方法：
  - `findByProjectIdAndAssigneeIdAndStatus(projectId, assigneeId, status)`
- 在 `StandupTaskTools.getInProgressTasks()` 中，替换掉原来：
  - 先 `findByProjectId(projectId)` 拉全量任务
  - 再用 Java `stream().filter(...)` 过滤 assignee/status

### 1.3 为什么这样改（性能原因）

- **避免全量扫描**：项目任务多时，“先查全量再过滤”会导致 DB→应用传输大量无用数据，并在 JVM 做额外 CPU/内存开销。
- **利用索引/优化器**：把过滤条件交给数据库执行，能更好利用索引与查询计划，通常显著降低 P95。

### 1.4 如何验证（建议）

- 开启 SQL/慢查询观测：确认查询语句包含 `project_id + assignee_id + status` 的过滤条件。
- 对比优化前后工具端点 `POST /api/v1/agent/tools/in-progress-tasks` 的平均耗时与 P95。

## 2. Python：LLM 客户端复用（避免重复初始化）

### 2.1 改动文件

- `backend/langchain-python/app/llm.py`

### 2.2 具体改动

- 给 `build_llm()` 增加 `@lru_cache(maxsize=1)`，使 `ChatOpenAI` 客户端在进程内复用。

### 2.3 为什么这样改（性能原因）

- 原实现每次调用都会 new 一个 LLM 客户端，可能导致额外初始化成本、连接复用差、并发时对象创建与 GC 压力上升。
- 复用后能让同一进程内的调用更稳定，降低抖动。

### 2.4 如何验证

- 观察日志：`[LLM] Building LLM instance...` 在同一进程生命周期内应只出现一次（或极少）。

## 3. Python：工具调用改为 async + httpx 连接池（并发与复用）

### 3.1 改动文件

- `backend/langchain-python/app/tools.py`

### 3.2 具体改动

- 将 `requests`（阻塞）替换为 `httpx.AsyncClient`：
  - 连接池复用（keep-alive）
  - 细粒度 timeout（connect/read/write/pool）
- 将工具调用函数改为 async：
  - `get_in_progress_tasks(...)`
  - `get_sprint_burndown(...)`
  - `evaluate_burndown_risk(...)`
- 增加 `trace_id` 透传到工具端点请求头：`X-Trace-Id`

### 3.3 为什么这样改（性能原因）

- 原来 3 个工具调用通常是串行叠加，且 `requests` 每次请求复用能力较弱，导致 RTT/握手开销放大。
- async + 连接池后：
  - 并发请求可以把“3 次累加”变为“取最大值”（显著降低 tools 总耗时）
  - keep-alive 减少握手/连接建立开销

### 3.4 如何验证

- 观察 Python 打印的 `[TIMING] tools_ms_total=...`：应明显下降。
- 在故障注入（让一个工具慢）时，总耗时应接近最慢工具（而不是三者之和）。

## 4. Python：新增 fast pipeline + 开关（减少 LLM 次数与 ReAct 不确定性）

### 4.1 改动文件

- `backend/langchain-python/app/agents.py`
- `backend/langchain-python/app/main.py`

### 4.2 具体改动

- 在 `agents.py` 新增 `run_fast_pipeline(...)`：
  - 并发拉取三类工具输出
  - 仅进行一次“汇总 JSON” LLM 调用（`SYSTEM_SUMMARIZER`）
  - 对模型 JSON 解析失败做兜底（确保接口可用）
- 降低 legacy ReAct 代理的成本：
  - `AgentExecutor(verbose=False)`
  - `max_iterations=3`
- 新增运行时开关（环境变量）：
  - `STANDUP_PIPELINE_MODE=fast|legacy`（默认 fast）
  - `TOOLS_CONCURRENT=true|false`
  - `LOG_STEP_TIMING=true|false`
- `main.py` 的 `/agent/standup/query` 改为 `async def`，并按 mode 选择执行路径。

### 4.3 为什么这样改（性能原因）

- 原流程：Planner + Data(ReAct 多轮) + Analyst + Writer，**LLM 调用次数多且串行叠加**，并且 ReAct 回合数不稳定（max_iterations=6）。
- 新 fast 流程把关键成本变为：
  - 并发工具调用耗时（取最大值）
  - 1 次 LLM 汇总耗时
  => 总耗时更可控，P95 更容易压下来。
- 保留 legacy 作为回滚路径，降低上线风险。

### 4.4 如何验证

- 设置 `STANDUP_PIPELINE_MODE=fast`，看日志 `Pipeline mode: fast`
- 观察 `[TIMING] llm_summarize_ms` 与 `tools_ms_total`，并对比旧版总耗时
- 切换 `STANDUP_PIPELINE_MODE=legacy`，确认能快速回滚

## 5. 依赖变更（Python requirements）

### 5.1 改动文件

- `backend/langchain-python/requirements.txt`

### 5.2 具体改动

- 移除了 `requests`（不再使用）
- 保留/使用 `httpx`（已在 requirements 中）

## 6. Postman：新增多场景测试集合

### 6.1 新增文件

- `postman/Burndown_Langchain_Standup_Optimization.postman_collection.json`

### 6.2 内容覆盖

- 复合问题（任务 + 燃尽 + 风险）
- 仅任务
- 仅燃尽/风险
- sprintId 缺失（null）
- 三个工具端点单独测试

并统一使用环境变量：

- `{{base_url}}`（建议使用现有 `Burndown-Local-Environment` 的 `base_url`）
- `{{project_id}} / {{sprint_id}} / {{user_id}}`

