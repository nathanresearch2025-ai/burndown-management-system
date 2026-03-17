from fastapi import FastAPI  # FastAPI 框架
from .schemas import StandupQueryRequest, StandupQueryResponse  # 请求与响应结构
from .agents import run_multi_agent, run_fast_pipeline, pipeline_mode, tools_concurrent_enabled, log_step_timing_enabled  # 编排入口
import logging
import time

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

app = FastAPI(title="LangChain Standup Agent")  # 创建 FastAPI 应用


@app.post("/agent/standup/query", response_model=StandupQueryResponse)  # 定义查询接口
async def standup_query(request: StandupQueryRequest):  # 处理站会问答请求
    logger.info("=" * 80)
    logger.info("[FastAPI] /agent/standup/query endpoint called")
    logger.info(f"Request data: question={request.question}, projectId={request.projectId}, "
                f"sprintId={request.sprintId}, userId={request.userId}")
    logger.info("=" * 80)

    try:
        mode = pipeline_mode()
        start = time.time()
        if mode == "legacy":
            result = run_multi_agent(
                question=request.question,
                project_id=request.projectId,
                sprint_id=request.sprintId or 0,
                user_id=request.userId,
            )
            answer = result["summary"]
            tools_used = ["getInProgressTasks", "getSprintBurndown", "evaluateBurndownRisk"]
            evidence = []
            risk_level = None
        else:
            result = await run_fast_pipeline(
                question=request.question,
                project_id=request.projectId,
                sprint_id=request.sprintId or 0,
                user_id=request.userId,
                trace_id=request.traceId,
                tools_concurrent=tools_concurrent_enabled(),
                log_step_timing=log_step_timing_enabled(),
            )
            answer = result.get("summary", "")
            tools_used = result.get("toolsUsed", ["getInProgressTasks", "getSprintBurndown", "evaluateBurndownRisk"])
            evidence = result.get("evidence", [])
            risk_level = result.get("riskLevel", None)

        response = StandupQueryResponse(  # 返回结构化响应
            answer=answer,  # 最终回答
            toolsUsed=tools_used,  # 记录使用工具
            evidence=evidence,  # 证据列表
            riskLevel=risk_level,  # 风险等级
        )

        logger.info("=" * 80)
        logger.info("[FastAPI] Request completed successfully")
        logger.info(f"Response summary length: {len(response.answer)} chars")
        logger.info(f"Pipeline mode: {mode}, total_ms={int((time.time() - start) * 1000)}")
        logger.info("=" * 80 + "\n")

        return response

    except Exception as e:
        logger.error("=" * 80)
        logger.error(f"[FastAPI] Error occurred: {e}")
        logger.error("=" * 80 + "\n")
        raise
