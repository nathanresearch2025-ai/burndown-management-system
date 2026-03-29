# 等价 Java：import java.util.List; import java.util.Optional;
from typing import TypedDict, Optional, Annotated   # TypedDict ≈ Java interface/record；Optional ≈ @Nullable；Annotated ≈ 带元数据的类型注解
import operator   # 等价 Java：import java.util.function.BinaryOperator; （operator.add 用于 LangGraph 消息追加语义）


# 等价 Java：public record StandupV2State(...) 或 public class StandupV2State { ... }
# TypedDict 相当于 Java 的强类型 Map/Record，规定每个 key 的类型
class StandupV2State(TypedDict):   # 等价 Java：public class StandupV2State implements Map<String,Object>
    """多 Agent 协同图的统一状态。
    messages 使用 Annotated + operator.add 支持 LangGraph 消息追加语义。
    """

    # ── 输入（请求注入）────────────────────────────────────────────
    question: str               # 等价 Java：private String question;         （用户原始问题）
    project_id: int             # 等价 Java：private int projectId;            （项目主键）
    sprint_id: Optional[int]    # 等价 Java：@Nullable private Integer sprintId; （Sprint 主键，可为 null）
    user_id: int                # 等价 Java：private int userId;               （当前用户主键）
    trace_id: Optional[str]     # 等价 Java：@Nullable private String traceId;  （链路追踪 ID，可为 null）

    # ── Supervisor 路由控制 ─────────────────────────────────────────
    # 当前应执行的 Agent 名称："data_agent" | "analyst_agent" | "writer_agent" | "FINISH"
    next: str   # 等价 Java：private String next; （Supervisor 写入，路由函数读取）

    # ── DataAgent 输出（ReAct 循环产出）────────────────────────────
    in_progress_raw: Optional[str]    # 等价 Java：@Nullable private String inProgressRaw;  （getInProgressTasks 原始结果）
    burndown_raw: Optional[str]       # 等价 Java：@Nullable private String burndownRaw;    （getSprintBurndown 原始结果）
    risk_raw: Optional[str]           # 等价 Java：@Nullable private String riskRaw;        （evaluateBurndownRisk 原始结果）
    data_summary: Optional[str]       # 等价 Java：@Nullable private String dataSummary;    （DataAgent 对工具数据的文字整理）
    tools_used: list[str]             # 等价 Java：private List<String> toolsUsed;          （实际调用的工具名列表）

    # ── AnalystAgent 输出 ──────────────────────────────────────────
    risk_level: str                   # 等价 Java：private String riskLevel;   （LOW | MEDIUM | HIGH | UNKNOWN）
    analysis: Optional[str]           # 等价 Java：@Nullable private String analysis;  （风险根因与趋势分析文本）

    # ── WriterAgent 输出 ───────────────────────────────────────────
    answer: str                       # 等价 Java：private String answer;        （最终中文站会报告）
    evidence: list[str]               # 等价 Java：private List<String> evidence; （关键证据片段，最多 6 条）

    # ── ReAct 消息历史（DataAgent 内部循环使用）────────────────────
    # operator.add 让每次节点返回的消息列表自动追加，而非覆盖
    # 等价 Java：框架层面的 @Accumulate 注解，每次节点返回值追加到列表末尾而非覆盖
    react_messages: Annotated[list, operator.add]   # 等价 Java：@Accumulate private List<String> reactMessages;

    # ── 错误信息 ───────────────────────────────────────────────────
    error: Optional[str]   # 等价 Java：@Nullable private String error;  （节点执行异常时写入）
