# `POST /agent/langchain/standup/query` 技术实现方案

## 1. 范围与原则

**目标**：在不破坏现有接口契约（请求/响应字段保持兼容）的前提下，把端到端耗时显著降低，并具备可观测、可回滚能力。

**改造边界**：

- Spring Boot 侧：只负责调用 Python LangChain 服务，不做多次 LLM 编排。
- Python 侧：对 `/agent/standup/query` 的编排与工具调用方式做优化。
- 后端工具端点：只做查询优化与缓存（不改变返回语义）。

## 2. 当前关键实现定位（便于对照改动）

- Spring 调 Python：`backend/src/main/java/com/burndown/aiagent/langchain/service/LangchainClientService.java`
- Spring 站会入口：`backend/src/main/java/com/burndown/aiagent/langchain/controller/LangchainStandupController.java`
- Python 入口：`backend/langchain-python/app/main.py`
- Python 编排：`backend/langchain-python/app/agents.py`
- Python 工具调用：`backend/langchain-python/app/tools.py`
- 工具端点（Spring）：`backend/src/main/java/com/burndown/aiagent/standup/controller/LangchainToolController.java`
- 工具查询实现：`backend/src/main/java/com/burndown/aiagent/standup/tool/StandupTaskTools.java` 等

## 3. 方案总览（按优先级逐步落地）

### 迭代 1（可观测 + 降噪 + 降回合，1~2 天）

- Python：
  - 关闭 `AgentExecutor(verbose=True)`，默认改为 `False`
  - `max_iterations: 6 → 3`
  - 引入分段耗时统计并输出到日志（traceId 贯穿）
  - 复用 LLM client（进程级单例）
- Spring：
  - `LangchainClientService` 记录调用耗时（Micrometer Timer + traceId 日志）
  - 收紧 `langchain.timeout`（建议 120s → 30s，配合降级）

### 迭代 2（工具并发 + 连接池，2~4 天）

- Python：
  - `requests` 改为 `httpx.AsyncClient`（连接池复用）+ `asyncio.gather` 并发调用 3 个工具
  - 增加工具调用的 per-tool 耗时与错误计数
- Spring：
  - 工具查询下推到 DB（避免全量扫描）
  - 可选：对工具结果加短 TTL 缓存（Redis/Caffeine）

### 迭代 3（减少 LLM 次数，3~5 天）

- Python：
  - 将 Planner/Analyst/Writer 合并为 1 次（或最多 2 次）LLM 调用
  - 方案推荐：**1 次“工具规划” + 1 次“最终总结”**，取消 ReAct Data Agent

## 4. 详细技术实现（可直接开工）

## 4.1 Python：从“多 Agent + ReAct”改为“Plan + 并发工具 + Summarize”

### 4.1.1 新的执行流程（推荐最终形态）

1. `LLM#1`：生成一个结构化计划（JSON），明确需要哪些工具与输出字段（但工具集合是固定 3 个，也可直接省略这步）。
2. 并发调用工具：
   - `getInProgressTasks(projectId,userId)`
   - `getSprintBurndown(projectId,sprintId)`
   - `evaluateBurndownRisk(projectId,sprintId)`
3. `LLM#2`：把工具原始输出整合成最终回答（同时输出 `toolsUsed`、`evidence`、`riskLevel`）。

> 若追求极致低延迟：可进一步把 `LLM#1` 省略（工具固定都调用），只保留 `LLM#2`。

### 4.1.2 代码改动点

#### A) `app/main.py`：把 endpoint 改为 async

- 将 `standup_query()` 改为 `async def`，便于 await 并发工具调用。

#### B) `app/tools.py`：改为 httpx + 连接池 + async

- 新增全局 `httpx.AsyncClient`（或通过 FastAPI lifespan 管理生命周期），设置：
  - `timeout`（建议 connect/read 分开设）
  - `limits`（连接池大小）
  - `headers`（traceId 透传）
- 将 `call_backend_tool()`、三个工具函数全部改为 async。

并发示例（关键点）：

- `asyncio.gather(get_in_progress_tasks(...), get_sprint_burndown(...), evaluate_burndown_risk(...))`

#### C) `app/llm.py`：LLM client 单例化

- 将 `build_llm()` 改成模块级缓存（例如 `functools.lru_cache(maxsize=1)`），避免每次 invoke 都重新 new。

#### D) `app/agents.py`：新增 `run_fast_pipeline()` 并替换旧 `run_multi_agent()`

- 新增：
  - `async def run_fast_pipeline(question, project_id, sprint_id, user_id, trace_id)`：
    - 并发拉取 tools
    - 一次 LLM 汇总
    - 返回 `answer/toolsUsed/evidence/riskLevel` 需要的字段
- 通过环境变量/配置开关控制：
  - `STANDUP_PIPELINE_MODE=legacy|fast`
  - 默认 `fast`，出现问题可回滚 `legacy`

### 4.1.3 Prompt（汇总阶段）建议

汇总 prompt 输出固定 JSON（再由程序组装响应），避免模型自由发挥导致解析不稳定：

- 必须输出字段：
  - `answer`：最终中文回答（结论 + 证据 + 建议）
  - `toolsUsed`：数组（固定 3 个或实际调用的）
  - `evidence`：数组（从工具输出里提取关键行/关键数值）
  - `riskLevel`：`LOW|MEDIUM|HIGH|UNKNOWN`

## 4.2 Spring：工具查询优化（避免全量扫描）

### 4.2.1 `StandupTaskTools.getInProgressTasks()` 优化

当前实现是：

- `taskRepository.findByProjectId(projectId)` 后 Java stream 过滤 assignee/status

建议改为：

- 在 `TaskRepository` 新增方法（按实体字段命名）：
  - `findByProjectIdAndAssigneeIdAndStatus(Long projectId, Long assigneeId, TaskStatus status)`
- `StandupTaskTools` 直接调用该 repo 方法，避免把全量任务加载到内存。

### 4.2.2 可选：短 TTL 缓存

如果 Redis 可用（项目已有）：

- 对工具结果加 `@Cacheable`（key 包含 projectId/sprintId/userId），TTL：
  - in-progress：10~30s
  - burndown/risk：30~60s

若不想引入 Redis TTL 管理复杂度，可先用 Caffeine（单机）验证效果。

## 4.3 Spring：调用 Python 的超时、降级与指标

### 4.3.1 `LangchainClientService` 增强

增加：

- Micrometer Timer：`langchain_client_duration_ms`
- Counter：`langchain_client_errors_total{type=timeout|5xx|4xx|io}`
- 日志：traceId + 总耗时 + 下游 statusCode

超时策略：

- `langchain.timeout` 建议收紧到 `30s`
- 超时返回：
  - 明确提示“服务繁忙/LLM 超时，请稍后重试”
  - 不中断主线程池（避免线程长期阻塞堆积）

### 4.3.2 traceId 透传

当前 traceId 写入 request body（`LangchainStandupRequest.traceId`）已具备基础。

建议再加 header：

- Spring → Python：`X-Trace-Id: {traceId}`
- Python → Spring 工具：同样带上 `X-Trace-Id`

## 5. 配置开关与回滚

### 5.1 Python 环境变量

- `STANDUP_PIPELINE_MODE=fast|legacy`
- `TOOLS_CONCURRENT=true|false`
- `LOG_STEP_TIMING=true|false`

### 5.2 Spring 配置

- `langchain.timeout: 30s`（从 120s 下调）
- 可选：`agent.standup.*` 增加“降级策略”参数（如允许返回简化回答）

## 6. 验证与测试计划（对应验收）

### 6.1 性能对比（黑盒）

- 固定请求参数与问题文本，循环 100 次
- 记录平均、P50、P95、P99、错误率
- 对比优化前后

### 6.2 分段耗时（白盒）

Python 日志输出（开启 `LOG_STEP_TIMING`）至少包含：

- `tools_ms_total`（并发后应显著下降）
- `llm_summarize_ms`
- `total_ms`

Spring 指标/日志至少包含：

- `langchain_client_duration_ms`（Timer）
- 错误类型计数（超时/网关错误）

### 6.3 正确性回归

用 5~10 条典型问题覆盖：

- 只问任务
- 只问燃尽
- 任务 + 燃尽 + 风险
- sprintId 缺失（按当前实现 sprintId=0 的处理，确认期望）
- 工具接口失败/超时（期望：给出降级回答并提示）

## 7. 交付物清单

- Python：
  - `app/main.py` async 化
  - `app/tools.py` httpx + async + 并发
  - `app/llm.py` LLM client 单例化
  - `app/agents.py` 新增 fast pipeline + 开关
- Spring：
  - `TaskRepository` 新增精准查询方法
  - `StandupTaskTools` 替换为 DB 下推过滤
  - `LangchainClientService` 增加指标/日志 + 调整超时

