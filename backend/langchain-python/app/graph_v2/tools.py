import logging
from langchain_core.tools import tool
from .. import tools as backend_tools

logger = logging.getLogger(__name__)

# ── LangChain Tool 包装 ────────────────────────────────────────────────────────
# 使用 @tool 装饰器将异步函数包装为 LangChain 可识别的工具
# DataAgent 的 ReAct 循环通过这些工具与 Java 后端交互

# 模块级上下文，由 DataAgent 节点在调用前注入
_ctx: dict = {}


def set_tool_context(project_id: int, sprint_id: int, user_id: int, trace_id: str | None):
    """在 DataAgent 节点执行前注入工具调用所需的上下文参数。"""
    _ctx["project_id"] = project_id
    _ctx["sprint_id"] = sprint_id
    _ctx["user_id"] = user_id
    _ctx["trace_id"] = trace_id


@tool
async def get_in_progress_tasks(dummy: str = "") -> str:
    """获取当前用户在指定项目中处于进行中状态的任务列表。
    返回任务名称、优先级、故事点等信息。
    参数 dummy 仅用于占位，无需传入。
    """
    project_id = _ctx.get("project_id", 0)
    user_id = _ctx.get("user_id", 0)
    trace_id = _ctx.get("trace_id")
    logger.info(f"[Tool] get_in_progress_tasks called: project_id={project_id}, user_id={user_id}")
    try:
        result = await backend_tools.get_in_progress_tasks(project_id, user_id, trace_id=trace_id)
        return result
    except Exception as e:
        logger.warning(f"[Tool] get_in_progress_tasks failed: {e}")
        return f"[工具调用失败] get_in_progress_tasks: {e}"


@tool
async def get_sprint_burndown(dummy: str = "") -> str:
    """获取指定 Sprint 的燃尽图数据，包含每日剩余故事点、理想线、实际线和偏差。
    参数 dummy 仅用于占位，无需传入。
    """
    project_id = _ctx.get("project_id", 0)
    sprint_id = _ctx.get("sprint_id", 0)
    trace_id = _ctx.get("trace_id")
    logger.info(f"[Tool] get_sprint_burndown called: project_id={project_id}, sprint_id={sprint_id}")
    try:
        result = await backend_tools.get_sprint_burndown(project_id, sprint_id, trace_id=trace_id)
        return result
    except Exception as e:
        logger.warning(f"[Tool] get_sprint_burndown failed: {e}")
        return f"[工具调用失败] get_sprint_burndown: {e}"


@tool
async def evaluate_burndown_risk(dummy: str = "") -> str:
    """评估指定 Sprint 的燃尽风险等级（LOW/MEDIUM/HIGH）及原因。
    参数 dummy 仅用于占位，无需传入。
    """
    project_id = _ctx.get("project_id", 0)
    sprint_id = _ctx.get("sprint_id", 0)
    trace_id = _ctx.get("trace_id")
    logger.info(f"[Tool] evaluate_burndown_risk called: project_id={project_id}, sprint_id={sprint_id}")
    try:
        result = await backend_tools.evaluate_burndown_risk(project_id, sprint_id, trace_id=trace_id)
        return result
    except Exception as e:
        logger.warning(f"[Tool] evaluate_burndown_risk failed: {e}")
        return f"[工具调用失败] evaluate_burndown_risk: {e}"


# DataAgent 可用的工具列表
DATA_AGENT_TOOLS = [get_in_progress_tasks, get_sprint_burndown, evaluate_burndown_risk]
