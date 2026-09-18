import logging
from langgraph.graph import StateGraph, END

from .state import StandupGraphState
from .nodes import (
    validate_input,
    fetch_core_tools,
    validate_data,
    retry_tools,
    enrich_data,
    summarize,
    finalize,
    early_exit,
)

logger = logging.getLogger(__name__)


# ── 条件路由函数 ──────────────────────────────────────────────────────────────

def route_after_validate_input(state: StandupGraphState) -> str:
    """validate_input 后：sprint_id 完全缺失走 early_exit，否则正常拉取工具数据。"""
    if "sprint_id" in state.get("missing_fields", []) and not state.get("sprint_id"):
        # sprint_id 缺失时仍可查进行中任务，由 early_exit 节点处理
        return "early_exit"
    return "fetch_core_tools"


def route_after_validate_data(state: StandupGraphState) -> str:
    """validate_data 后三路路由：
    - 有工具失败且未重试过 → retry_tools
    - burndown 缺失且未 enrich 过 → enrich_data
    - 否则直接 summarize
    """
    if state.get("tool_errors") and state.get("retry_count", 0) < 1:
        logger.info("[Route] validate_data → retry_tools")
        return "retry_tools"
    if "burndown" in state.get("missing_fields", []) and not state.get("enriched"):
        logger.info("[Route] validate_data → enrich_data")
        return "enrich_data"
    logger.info("[Route] validate_data → summarize")
    return "summarize"


def route_after_retry(state: StandupGraphState) -> str:
    """retry_tools 后：burndown 仍缺失则 enrich，否则直接 summarize。"""
    if "burndown" in state.get("missing_fields", []) and not state.get("enriched"):
        return "enrich_data"
    return "summarize"


# ── 图构建 ────────────────────────────────────────────────────────────────────

def build_graph():
    """构建并编译 LangGraph 站会问答图，返回可调用的 compiled graph。"""
    workflow = StateGraph(StandupGraphState)

    # 注册节点
    workflow.add_node("validate_input", validate_input)
    workflow.add_node("fetch_core_tools", fetch_core_tools)
    workflow.add_node("validate_data", validate_data)
    workflow.add_node("retry_tools", retry_tools)
    workflow.add_node("enrich_data", enrich_data)
    workflow.add_node("summarize", summarize)
    workflow.add_node("finalize", finalize)
    workflow.add_node("early_exit", early_exit)

    # 入口
    workflow.set_entry_point("validate_input")

    # 边：validate_input → (early_exit | fetch_core_tools)
    workflow.add_conditional_edges(
        "validate_input",
        route_after_validate_input,
        {
            "early_exit": "early_exit",
            "fetch_core_tools": "fetch_core_tools",
        },
    )

    # 边：fetch_core_tools → validate_data（固定）
    workflow.add_edge("fetch_core_tools", "validate_data")

    # 边：validate_data → (retry_tools | enrich_data | summarize)
    workflow.add_conditional_edges(
        "validate_data",
        route_after_validate_data,
        {
            "retry_tools": "retry_tools",
            "enrich_data": "enrich_data",
            "summarize": "summarize",
        },
    )

    # 边：retry_tools → (enrich_data | summarize)
    workflow.add_conditional_edges(
        "retry_tools",
        route_after_retry,
        {
            "enrich_data": "enrich_data",
            "summarize": "summarize",
        },
    )

    # 边：enrich_data → summarize（固定）
    workflow.add_edge("enrich_data", "summarize")

    # 边：summarize → finalize（固定）
    workflow.add_edge("summarize", "finalize")

    # 终止边
    workflow.add_edge("finalize", END)
    workflow.add_edge("early_exit", END)

    return workflow.compile()
