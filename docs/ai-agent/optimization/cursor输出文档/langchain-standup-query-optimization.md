# `POST /agent/langchain/standup/query` 性能优化需求文档

## 1. 背景与目标

当前 `{{baseUrl}}/agent/langchain/standup/query` 调用**非常缓慢**，影响站会助手可用性与用户体验。

本优化目标：

- **P95 < 8s**（单用户、正常网络、无异常重试）
- **P99 < 15s**
- 出现外部依赖抖动（LLM/网络）时可**快速失败或降级**（不把线程卡死到超时上限）
- 能**量化定位**慢在哪里（分段耗时：Spring→Python、Python→LLM、Python→工具调用、DB）

## 2. 现状调用链与关键事实

### 2.1 端到端链路

- Spring Boot：`POST /api/v1/agent/langchain/standup/query`
  - `LangchainStandupController` → `LangchainClientService.queryStandup()`
  - Java `HttpClient` 调用 Python 服务：`${langchain.base-url}/agent/standup/query`
  - 当前配置：`langchain.timeout: 120s`
- Python（FastAPI）：`POST /agent/standup/query`
  - `run_multi_agent()` 多 Agent 编排
  - 期间会调用后端工具（Spring Boot）：`/api/v1/agent/tools/*`

### 2.2 Python 端“必然串行”的外部调用次数

根据 `backend/langchain-python/app/agents.py` 的实现，**一次请求至少包含：**

- **4 次 LLM 调用（串行）**
  - Planner：`invoke_llm(SYSTEM_PLANNER, question)`
  - Data Agent：`create_react_agent(...)`（Agent 内部会与 LLM 多轮交互，且 `max_iterations=6`）
  - Analyst：`invoke_llm(SYSTEM_ANALYST, "数据如下: ...")`
  - Writer：`invoke_llm(SYSTEM_WRITER, "分析结论: ...")`
- **3 次后端工具 HTTP 调用（通常串行）**
  - `getInProgressTasks` → `POST /api/v1/agent/tools/in-progress-tasks`
  - `getSprintBurndown` → `POST /api/v1/agent/tools/sprint-burndown`
  - `evaluateBurndownRisk` → `POST /api/v1/agent/tools/burndown-risk`

结论：当前设计天然会把延迟叠加：  
**总耗时 ≈ 4×LLM RTT + 工具 HTTP RTT×3 + Agent 内部 ReAct 多轮 + DB 查询**。

### 2.3 其它慢点放大器

- **LLM 客户端反复创建**：`invoke_llm()` 与 `build_data_agent()` 每次都 `build_llm()`，可能导致额外初始化/握手/连接复用差。
- **阻塞 HTTP + 无连接复用（Python→Spring）**：`app/tools.py` 用 `requests.post(...)`，每次新建连接概率更高，且无法并发。
- **Data Agent 的 ReAct 循环成本不可控**：`max_iterations=6`，且 `verbose=True` 输出大量日志；工具描述/提示词也偏“要求逐步输出”，token 消耗与延迟都更高。
- **后端工具实现存在全量扫描风险**：如 `StandupTaskTools.getInProgressTasks()` 先 `findByProjectId()` 再 `stream().filter()`，当项目任务多时会慢且占用内存（应下推到 DB 侧过滤）。
- **日志开销**：Spring/Python 都在打印较大 payload/response（尤其 `verbose=True`、工具响应截断打印等），在高并发下会明显拖慢。

## 3. 慢的原因归因（可验证）

### 3.1 主要根因（高概率）

- **根因 A：一次请求触发多次 LLM 串行调用**  
  任何一次 LLM 调用的网络/排队/生成延迟都会直接叠加到总耗时。

- **根因 B：Data Agent ReAct 可能产生多轮内部交互**  
  即便工具只有 3 个，ReAct 的 Thought/Action/Observation 循环仍可能多轮，导致额外 LLM token 与回合。

- **根因 C：Python→Spring 工具调用为阻塞串行**  
  工具调用耗时（DB 查询 + HTTP RTT）被串行叠加。

### 3.2 次要根因（中概率）

- **根因 D：后端工具查询不够“DB 友好”**  
  例如“先查全量再过滤”会把 DB/网络/Java 端 CPU 一起拖慢。

- **根因 E：缺少分段耗时与 trace**  
  目前只能看到总耗时，无法快速定位是“LLM 慢”还是“工具慢”。

## 4. 优化方案（按优先级）

### 4.1 P0（立刻做，收益最大，风险可控）

- **P0-1：合并 LLM 调用次数（4 → 1~2）**
  - 方案：把 Planner/Analyst/Writer 合并为一次“结构化输出”的单次 LLM 调用：
    - LLM 先输出 plan（JSON）+ 数据需求（需要哪些工具）+ 结论模板
    - 工具数据拿到后，第二次 LLM 生成最终回答（可选）
  - 目标：把“LLM 串行次数”从 4 次降到 1~2 次，延迟立刻下降。

- **P0-2：工具调用并发化（Python 侧）**
  - 方案：将 `requests` 替换为 `httpx.AsyncClient` 或 `httpx.Client` + 连接池，并发调用 3 个工具（`asyncio.gather`）。
  - 目标：工具端耗时由“3 次串行累加”变成“取最大值”。

- **P0-3：LLM/HTTP 客户端复用**
  - Python：复用 `ChatOpenAI` 实例（进程级单例）+ 复用 HTTP client（连接池）。
  - Spring：`HttpClient` 已是字段级复用，但建议显式配置 connect/read timeout 并补充指标。

- **P0-4：强制缩短 ReAct 或关闭 verbose**
  - 方案：`AgentExecutor(verbose=False)`；降低 `max_iterations` 到 3；提示词减少“逐步输出”要求，减少 token 与回合。
  - 目标：压低 token 与 LLM 回合数，提高稳定性。

### 4.2 P1（后端查询与缓存）

- **P1-1：把过滤下推到数据库（避免全量扫描）**
  - 对 `getInProgressTasks`：新增 repository 方法（示例意图）：
    - `findByProjectIdAndAssigneeIdAndStatus(projectId, userId, IN_PROGRESS)`
  - 对燃尽与风险评估：确认查询只取必要字段与最新点位，避免读全量点。

- **P1-2：工具结果缓存（短 TTL）**
  - 站会场景数据短时间内变化有限：
    - In-progress tasks：TTL 10~30s
    - Sprint burndown：TTL 30~60s
    - Risk eval：可直接根据 burndown 计算，或 TTL 30~60s
  - 缓存位置：
    - Spring 侧：Redis（项目已有 Redis），或 Caffeine（单机）
    - Python 侧：内存 LRU（进程级）+ key = `(projectId,sprintId,userId)`

### 4.3 P2（可观测性与治理）

- **P2-1：端到端 traceId 贯穿与分段耗时**
  - Spring：把 `traceId` 注入到调用 Python 的请求 header / body，并在日志 MDC 打印；同时增加 Micrometer timer：
    - `langchain_client_duration_ms`
    - `langchain_client_errors_total`
  - Python：在每个步骤打印耗时（Planner/Data/Tools/Analyst/Writer），并返回到响应的 `evidence` 或隐藏字段中（仅调试开关开启时）。

- **P2-2：配置合理的超时与快速失败**
  - Spring→Python：不要默认 120s；建议 20~30s，并在超时后返回“降级回答/提示稍后重试”。
  - Python→LLM：设置 request timeout（如果底层支持）与重试策略（指数退避、最多 1 次）。

## 5. 验收标准（可量化）

### 5.1 指标目标

- **P95 < 8s，P99 < 15s**（稳定网络 + LLM 正常）
- 工具调用并发后：`tools_total_time` 约等于三者中的最大值
- 单请求 LLM 调用次数：从 4 次降到 1~2 次（以日志/计数器验证）

### 5.2 验证方法（优化前/后对比）

- **方法 A：黑盒压测**
  - 用固定输入（相同 question/projectId/sprintId/userId）压测 50~200 次
  - 记录平均、P50、P95、P99、错误率
  - 对比优化前后差异

- **方法 B：白盒分段耗时**
  - Spring：记录 `LangchainClientService.queryStandup()` 的总耗时与异常类型
  - Python：记录每一步耗时：
    - `planner_ms`
    - `data_agent_ms`
    - `tools_ms`（并发后应显著下降）
    - `analyst_ms`
    - `writer_ms`
  - 目标：能明确指出“慢在 LLM 还是慢在工具/DB”

- **方法 C：工具侧查询耗时**
  - 在 `/agent/tools/*` 工具端点上增加 timer，按 toolName 聚合 P95
  - 验证 DB 下推过滤是否降低 `getInProgressTasks` 的平均与 P95

## 6. 风险与回滚

- **风险：LLM 合并调用可能影响回答质量**  
  - 缓解：保留“2 次 LLM”方案（先取数据/再总结），并通过回归用例对齐输出格式。
- **风险：并发工具调用增加后端瞬时压力**  
  - 缓解：限制并发（3 个工具固定），并对同 key 请求做去重/缓存。
- **回滚策略**：通过配置开关切回旧流程（如 `MULTI_AGENT_ENABLED=true/false`、`TOOLS_CONCURRENT=true/false`）。

## 7. 建议的实施拆分（按迭代）

- **迭代 1（1~2 天）**：关 verbose、降低 max_iterations、复用 client、加分段耗时日志与指标
- **迭代 2（2~4 天）**：工具调用并发化 + 后端查询下推过滤
- **迭代 3（3~5 天）**：合并 LLM 调用（4→2→1），引入缓存与请求去重

