# LangGraph 应用场景分析与简单需求（基于当前后端）

> 项目：Burndown Management System  
> 分析范围：`backend/langchain-python`（FastAPI + LangChain）及其对 Java 后端工具接口调用  
> 日期：2026-03-23

---

## 1. 当前后端 AI 架构现状（从代码看）

### 1.1 已有能力

1. **统一入口**：`/agent/standup/query`（FastAPI）
2. **双流水线模式**：
   - `fast`：并行调用 3 个工具 + 1 次 LLM 汇总（默认）
   - `legacy`：Planner → Data Agent(ReAct) → Analyst → Writer（串行多 Agent）
3. **工具层封装**：通过 Python 工具调用 Java 后端接口：
   - `getInProgressTasks`
   - `getSprintBurndown`
   - `evaluateBurndownRisk`
4. **基础工程能力**：
   - traceId 透传
   - 工具并发开关（`TOOLS_CONCURRENT`）
   - 计时日志开关（`LOG_STEP_TIMING`）

### 1.2 当前限制（为什么适合引入 LangGraph）

虽然已有多 Agent 和并发工具调用，但整体仍偏“流程脚本化”，在以下方面存在不足：

1. **状态管理弱**：多步骤中间状态没有明确的统一 state schema
2. **条件分支有限**：主要靠 if/else，复杂分支可读性与扩展性不足
3. **循环修正能力弱**：缺少“信息不足→补查→再判断”的显式图式循环
4. **节点可观测性不足**：缺少图级别的节点执行轨迹、重试策略、失败转移策略

结论：当前代码已具备“工具 + LLM + 异步并发”的基础，非常适合作为 LangGraph 落地起点。

---

## 2. 在本项目中适合的 LangGraph 应用场景

### 场景 A：站会问答增强（优先级最高，改造成本最低）

**目标**：把现有 `run_fast_pipeline` 进化为图流程，增强可解释性与容错。  
**适配原因**：你们已经有成熟工具和 API，直接把“工具调用+汇总”拆成图节点即可。

可解决的问题：
- sprintId 缺失时智能降级而非仅固定提示
- 某个工具失败时可重试或走 fallback 节点
- 信息不足时自动补充查询（如追加查询最近 sprint）

### 场景 B：燃尽风险根因诊断 Agent

**目标**：不仅输出 LOW/MEDIUM/HIGH，还输出“为什么 + 怎么办”。

图中可加入：
- 风险判断节点
- 根因假设节点
- 验证节点（再调工具）
- 行动建议节点

### 场景 C：Sprint 规划辅助 Agent（新能力）

**目标**：输入 project/sprint，上下文化输出“是否超载、风险点、建议调配”。

适配性：需要多源数据、分支判断和报告生成，正是 LangGraph 的强项。

---

## 3. 简单需求（MVP）：LangGraph 版站会问答增强

> 为了更快落地，建议先做“在现有接口上平滑升级”的版本。

### 3.1 需求描述

在 `backend/langchain-python` 中新增一条 LangGraph 执行链路，替代或并行现有 `run_fast_pipeline`，用于 `/agent/standup/query` 请求处理。该链路需支持：

1. 三个后端工具的并行获取
2. 工具失败重试（最多 1 次）
3. 信息完整性检查（是否可直接回答）
4. 信息不足时触发补充查询节点
5. 最终输出结构化结果：`answer/toolsUsed/evidence/riskLevel`

### 3.2 输入输出（与现有接口兼容）

**输入**：
- `question: str`
- `projectId: int`
- `sprintId: int | null`
- `userId: int`
- `traceId: str | null`

**输出**（保持不变）：
- `answer: str`
- `toolsUsed: string[]`
- `evidence: string[]`
- `riskLevel: LOW | MEDIUM | HIGH | UNKNOWN`

### 3.3 LangGraph 状态设计（建议）

```python
class StandupGraphState(TypedDict):
    question: str
    project_id: int
    sprint_id: int
    user_id: int
    trace_id: str | None

    # 工具原始输出
    in_progress_raw: str | None
    burndown_raw: str | None
    risk_raw: str | None

    # 过程控制
    missing_info: list[str]
    retry_count: int
    tool_errors: list[str]

    # 最终输出
    answer: str
    tools_used: list[str]
    evidence: list[str]
    risk_level: str
```

### 3.4 图节点（MVP）

1. `fetch_core_tools`：并行调用 3 个工具
2. `validate_data`：检查数据完整性（sprint 缺失、工具报错、关键信息为空）
3. `retry_or_fallback`：有错误则重试一次；仍失败则记录降级说明
4. `optional_enrich`：信息不足时补充一次查询（可先做轻量版本）
5. `summarize`：调用 LLM 生成最终结构化 JSON
6. `finalize`：兜底解析与响应映射

### 3.5 验收标准（简单可测）

1. 正常请求下，平均响应时间不高于现有 `fast pipeline` 的 1.2 倍
2. 任一工具 5xx 时，系统仍可返回可读结论（非 500）
3. `sprintId` 缺失时，`riskLevel=UNKNOWN` 且 answer 说明原因
4. 返回 JSON 可稳定解析，字段完整
5. 日志中可看到节点级耗时与节点执行路径

---

## 4. 推荐落地步骤（1 周 MVP）

1. 新建 `app/graph/` 目录，定义 state 与节点函数
2. 把现有 `run_fast_pipeline` 里的工具调用与汇总逻辑迁移为图节点
3. 在 `main.py` 增加 `STANDUP_PIPELINE_MODE=langgraph` 分支
4. 保留 `fast/legacy` 作为回滚开关
5. 补充 5 条核心 E2E 用例（正常、无 sprint、工具超时、工具 5xx、LLM 非法 JSON）

---

## 5. 结论

结合现有代码基础，**最合适的 LangGraph 首个应用场景是“站会问答增强”**：
- 改造成本低（复用现有工具接口与响应结构）
- 业务价值直接（稳定性、可解释性、容错能力明显提升）
- 可作为后续“燃尽根因诊断”和“Sprint 规划 Agent”的技术模板
