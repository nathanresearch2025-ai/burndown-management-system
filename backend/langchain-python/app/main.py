from fastapi import FastAPI  # FastAPI 框架
from .schemas import StandupQueryRequest, StandupQueryResponse  # 请求与响应结构
from .agents import run_multi_agent  # 多 Agent 编排入口

app = FastAPI(title="LangChain Standup Agent")  # 创建 FastAPI 应用


@app.post("/agent/standup/query", response_model=StandupQueryResponse)  # 定义查询接口
def standup_query(request: StandupQueryRequest):  # 处理站会问答请求
    result = run_multi_agent(  # 调用多 Agent 流水线
        question=request.question,  # 用户问题
        project_id=request.projectId,  # 项目 ID
        sprint_id=request.sprintId or 0,  # Sprint ID（为空则置 0）
        user_id=request.userId,  # 用户 ID
    )

    return StandupQueryResponse(  # 返回结构化响应
        answer=result["summary"],  # 最终摘要
        toolsUsed=["getInProgressTasks", "getSprintBurndown", "evaluateBurndownRisk"],  # 记录使用工具
        evidence=[],  # 证据列表（暂为空）
        riskLevel=None,  # 风险等级（可扩展）
    )
