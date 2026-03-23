# Pydantic BaseModel：FastAPI 用它自动完成请求体反序列化和响应体序列化
from pydantic import BaseModel
# List：声明列表类型字段；Optional：声明可空字段（等价于 Union[X, None]）
from typing import List, Optional


class StandupQueryRequest(BaseModel):
    """站会查询接口的请求体结构。
    FastAPI 收到 POST JSON 后自动反序列化为此类，字段类型不匹配时返回 422 Unprocessable Entity。
    """

    # 用户的自然语言问题，例如：「今天 Sprint 有风险吗？」
    question: str

    # 项目 ID，对应 Spring Boot 数据库中的 project.id
    projectId: int

    # Sprint ID，可选；不传时 agents.py 会默认为 0，并跳过燃尽图相关工具调用
    sprintId: Optional[int] = None

    # 当前操作用户的 ID，用于拉取「我的进行中任务」
    userId: int

    # 时区标识，预留字段，当前未参与业务逻辑，默认上海时区
    timezone: Optional[str] = "Asia/Shanghai"

    # 链路追踪 ID，透传给后端 HTTP Header X-Trace-Id，便于跨服务日志关联
    traceId: Optional[str] = None


class ToolResult(BaseModel):
    """单个工具调用结果的结构（预留，当前代码中未直接使用）。"""

    # 工具名称，如 "getInProgressTasks"
    name: str

    # 工具返回的原始字符串内容
    output: str


class StandupQueryResponse(BaseModel):
    """站会查询接口的响应体结构。
    FastAPI 依据 response_model 自动将返回对象序列化为 JSON。
    """

    # LLM 生成的中文最终回答（结论 + 证据 + 建议）
    answer: str

    # 本次实际调用的工具名列表，例如 ["getInProgressTasks", "getSprintBurndown"]
    toolsUsed: List[str]

    # 从工具输出中摘取的关键证据片段，最多 6 条
    evidence: List[str]

    # 风险等级：LOW / MEDIUM / HIGH / UNKNOWN，由 LLM 根据燃尽数据判断
    # 未提供 sprintId 或数据缺失时为 UNKNOWN
    riskLevel: Optional[str] = None
