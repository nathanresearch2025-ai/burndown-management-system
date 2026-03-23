# FastAPI：Python 高性能异步 Web 框架，自动生成 OpenAPI 文档，支持类型校验
from fastapi import FastAPI
# 请求体和响应体的 Pydantic 数据结构
from .schemas import StandupQueryRequest, StandupQueryResponse
# run_multi_agent：legacy pipeline 编排入口（4 Agent 串行）
# run_fast_pipeline：fast pipeline 编排入口（并行工具 + 单次 LLM 汇总）
# pipeline_mode：读取 STANDUP_PIPELINE_MODE 环境变量，决定走哪条流水线
# tools_concurrent_enabled：读取 TOOLS_CONCURRENT 环境变量，决定工具是否并行调用
# log_step_timing_enabled：读取 LOG_STEP_TIMING 环境变量，决定是否打印耗时日志
from .agents import run_multi_agent, run_fast_pipeline, pipeline_mode, tools_concurrent_enabled, log_step_timing_enabled
import logging
import time

# 配置根日志：INFO 级别，格式包含时间、模块名、日志级别和消息
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
# 获取当前模块的 logger（名称为模块完整路径，如 app.main）
logger = logging.getLogger(__name__)

# 创建 FastAPI 应用实例，title 显示在自动生成的 Swagger UI 页面标题
app = FastAPI(title="LangChain Standup Agent")


# @app.post：注册 POST 路由，路径为 /agent/standup/query
# response_model：FastAPI 依据此模型自动序列化返回值并过滤多余字段
@app.post("/agent/standup/query", response_model=StandupQueryResponse)
async def standup_query(request: StandupQueryRequest):  # FastAPI 自动将请求体 JSON 反序列化为 StandupQueryRequest
    """站会智能问答接口。
    根据环境变量 STANDUP_PIPELINE_MODE 选择 fast（默认）或 legacy 流水线。
    fast：并行调用三个工具 → Summarizer LLM 一次汇总，延迟低。
    legacy：Planner → Data Agent（ReAct）→ Analyst → Writer 四步串行，推理链清晰。
    """
    # 打印分隔线和请求参数，便于日志中快速定位每次请求的边界
    logger.info("=" * 80)
    logger.info("[FastAPI] /agent/standup/query endpoint called")
    logger.info(f"Request data: question={request.question}, projectId={request.projectId}, "
                f"sprintId={request.sprintId}, userId={request.userId}")
    logger.info("=" * 80)

    try:
        # 读取流水线模式："fast"（默认）或 "legacy"
        mode = pipeline_mode()
        # 记录请求开始时间，用于计算总耗时
        start = time.time()

        if mode == "legacy":
            # Legacy pipeline：同步调用，在协程中运行会阻塞事件循环（生产环境不推荐）
            result = run_multi_agent(
                question=request.question,
                project_id=request.projectId,
                sprint_id=request.sprintId or 0,  # None 转为 0，避免下游判断出错
                user_id=request.userId,
            )
            answer = result["summary"]  # legacy pipeline 最终回答在 summary 字段
            # legacy pipeline 固定使用三个工具，无结构化 evidence 和 riskLevel
            tools_used = ["getInProgressTasks", "getSprintBurndown", "evaluateBurndownRisk"]
            evidence = []
            risk_level = None
        else:
            # Fast pipeline：全异步，await 不阻塞事件循环，适合生产环境高并发
            result = await run_fast_pipeline(
                question=request.question,
                project_id=request.projectId,
                sprint_id=request.sprintId or 0,
                user_id=request.userId,
                trace_id=request.traceId,                      # 链路追踪 ID 透传
                tools_concurrent=tools_concurrent_enabled(),   # 工具是否并行
                log_step_timing=log_step_timing_enabled(),     # 是否打印耗时
            )
            answer = result.get("summary", "")  # LLM 汇总后的中文回答
            # 实际调用的工具列表（LLM 从 JSON 输出中提取）
            tools_used = result.get("toolsUsed", ["getInProgressTasks", "getSprintBurndown", "evaluateBurndownRisk"])
            evidence = result.get("evidence", [])       # 关键证据片段
            risk_level = result.get("riskLevel", None)  # 风险等级

        # 构造响应对象，FastAPI 依据 response_model 自动序列化为 JSON
        response = StandupQueryResponse(
            answer=answer,          # 最终中文回答
            toolsUsed=tools_used,   # 使用的工具列表
            evidence=evidence,      # 证据片段列表
            riskLevel=risk_level,   # 风险等级
        )

        logger.info("=" * 80)
        logger.info("[FastAPI] Request completed successfully")
        logger.info(f"Response summary length: {len(response.answer)} chars")
        # 打印流水线模式和总耗时，便于性能监控
        logger.info(f"Pipeline mode: {mode}, total_ms={int((time.time() - start) * 1000)}")
        logger.info("=" * 80 + "\n")

        return response

    except Exception as e:
        # 捕获所有异常，记录日志后重新抛出
        # FastAPI 会将未处理异常转为 500 Internal Server Error
        logger.error("=" * 80)
        logger.error(f"[FastAPI] Error occurred: {e}")
        logger.error("=" * 80 + "\n")
        raise
