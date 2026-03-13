from pydantic import BaseModel  # Pydantic 基类
from typing import List, Optional  # 类型注解


class StandupQueryRequest(BaseModel):  # 站会请求的入参结构
    question: str  # 用户问题
    projectId: int  # 项目 ID
    sprintId: Optional[int] = None  # Sprint ID（可选）
    userId: int  # 用户 ID
    timezone: Optional[str] = "Asia/Shanghai"  # 时区，默认上海
    traceId: Optional[str] = None  # 追踪 ID（可选）


class ToolResult(BaseModel):  # 工具调用结果结构
    name: str  # 工具名称
    output: str  # 工具返回内容


class StandupQueryResponse(BaseModel):  # 站会返回结构
    answer: str  # 最终回答
    toolsUsed: List[str]  # 使用的工具列表
    evidence: List[str]  # 证据片段
    riskLevel: Optional[str] = None  # 风险等级（可选）
