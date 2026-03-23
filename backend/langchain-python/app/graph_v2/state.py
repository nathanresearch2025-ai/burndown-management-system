from typing import TypedDict, Optional, Annotated
import operator


class StandupV2State(TypedDict):
    """多 Agent 协同图的统一状态。
    messages 使用 Annotated + operator.add 支持 LangGraph 消息追加语义。
    """

    # ── 输入（请求注入）────────────────────────────────────────────
    question: str
    project_id: int
    sprint_id: Optional[int]
    user_id: int
    trace_id: Optional[str]

    # ── Supervisor 路由控制 ─────────────────────────────────────────
    # 当前应执行的 Agent 名称："data_agent" | "analyst_agent" | "writer_agent" | "FINISH"
    next: str

    # ── DataAgent 输出（ReAct 循环产出）────────────────────────────
    in_progress_raw: Optional[str]    # getInProgressTasks 原始结果
    burndown_raw: Optional[str]       # getSprintBurndown 原始结果
    risk_raw: Optional[str]           # evaluateBurndownRisk 原始结果
    data_summary: Optional[str]       # DataAgent 对工具数据的文字整理
    tools_used: list[str]             # 实际调用的工具名列表

    # ── AnalystAgent 输出 ──────────────────────────────────────────
    risk_level: str                   # LOW | MEDIUM | HIGH | UNKNOWN
    analysis: Optional[str]           # 风险根因与趋势分析文本

    # ── WriterAgent 输出 ───────────────────────────────────────────
    answer: str                       # 最终中文站会报告
    evidence: list[str]               # 关键证据片段（最多 6 条）

    # ── ReAct 消息历史（DataAgent 内部循环使用）────────────────────
    # operator.add 让每次节点返回的消息列表自动追加，而非覆盖
    react_messages: Annotated[list, operator.add]

    # ── 错误信息 ───────────────────────────────────────────────────
    error: Optional[str]
