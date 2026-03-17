from fastapi import FastAPI  # FastAPI 框架
from .schemas import StandupQueryRequest, StandupQueryResponse  # 请求与响应结构
from .agents import run_multi_agent  # 多 Agent 编排入口
import logging

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

app = FastAPI(title="LangChain Standup Agent")  # 创建 FastAPI 应用


@app.post("/agent/standup/query", response_model=StandupQueryResponse)  # 定义查询接口
def standup_query(request: StandupQueryRequest):  # 处理站会问答请求
    logger.info("=" * 80)
    logger.info("[FastAPI] /agent/standup/query endpoint called")
    logger.info(f"Request data: question={request.question}, projectId={request.projectId}, "
                f"sprintId={request.sprintId}, userId={request.userId}")
    logger.info("=" * 80)

    try:
        result = run_multi_agent(  # 调用多 Agent 流水线
            question=request.question,  # 用户问题
            project_id=request.projectId,  # 项目 ID
            sprint_id=request.sprintId or 0,  # Sprint ID（为空则置 0）
            user_id=request.userId,  # 用户 ID
        )

        response = StandupQueryResponse(  # 返回结构化响应
            answer=result["summary"],  # 最终摘要
            toolsUsed=["getInProgressTasks", "getSprintBurndown", "evaluateBurndownRisk"],  # 记录使用工具
            evidence=[],  # 证据列表（暂为空）
            riskLevel=None,  # 风险等级（可扩展）
        )

        logger.info("=" * 80)
        logger.info("[FastAPI] Request completed successfully")
        logger.info(f"Response summary length: {len(response.answer)} chars")
        logger.info("=" * 80 + "\n")

        return response

    except Exception as e:
        logger.error("=" * 80)
        logger.error(f"[FastAPI] Error occurred: {e}")
        logger.error("=" * 80 + "\n")
        raise
