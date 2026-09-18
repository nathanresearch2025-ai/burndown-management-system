from typing import TypedDict, Optional


class StandupGraphState(TypedDict):
    """LangGraph 站会问答图的统一状态对象。
    所有节点共享此状态，每个节点只修改自己负责的字段。
    """

    # ── 输入字段（由请求注入，节点只读）──────────────────────────
    question: str               # 用户自然语言问题
    project_id: int             # 项目 ID
    sprint_id: Optional[int]    # Sprint ID，None 表示未提供
    user_id: int                # 操作用户 ID
    trace_id: Optional[str]     # 链路追踪 ID，透传给 Java 后端

    # ── 工具原始输出（fetch_core_tools 节点填充）─────────────────
    in_progress_raw: Optional[str]   # getInProgressTasks 原始返回
    burndown_raw: Optional[str]      # getSprintBurndown 原始返回
    risk_raw: Optional[str]          # evaluateBurndownRisk 原始返回

    # ── 过程控制（validate_data / retry 节点使用）────────────────
    tool_errors: list[str]      # 失败工具名列表，如 ["getSprintBurndown"]
    missing_fields: list[str]   # 数据缺失字段，如 ["burndown"]
    retry_count: int            # 已重试次数，最多重试 1 次
    enriched: bool              # 是否已执行过补充查询节点

    # ── 最终输出（summarize / finalize 节点填充）─────────────────
    answer: str                 # LLM 生成的中文回答
    tools_used: list[str]       # 实际调用的工具名列表
    evidence: list[str]         # 关键证据片段（最多 6 条）
    risk_level: str             # LOW | MEDIUM | HIGH | UNKNOWN
