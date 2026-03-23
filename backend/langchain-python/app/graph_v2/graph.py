import logging
from langgraph.graph import StateGraph, END

from .state import StandupV2State
from .nodes import (
    supervisor_node,
    data_agent_node,
    analyst_agent_node,
    writer_agent_node,
)

logger = logging.getLogger(__name__)


# ── 条件路由：Supervisor 决策 → 对应 Agent ────────────────────────────────────

def route_supervisor(state: StandupV2State) -> str:
    """读取 Supervisor 写入 state['next']，路由到对应 Agent 节点或 END。"""
    next_agent = state.get("next", "data_agent")
    logger.info(f"[Route] supervisor → {next_agent}")
    if next_agent == "FINISH":
        return END
    return next_agent


# ── 图构建 ────────────────────────────────────────────────────────────────────

def build_graph_v2():
    """构建多 Agent + ReAct 完整版 LangGraph 图。

    拓扑结构：
        supervisor → data_agent (ReAct 循环) → supervisor
        supervisor → analyst_agent → supervisor
        supervisor → writer_agent → supervisor
        supervisor → END
    """
    workflow = StateGraph(StandupV2State)

    # 注册节点
    workflow.add_node("supervisor", supervisor_node)
    workflow.add_node("data_agent", data_agent_node)
    workflow.add_node("analyst_agent", analyst_agent_node)
    workflow.add_node("writer_agent", writer_agent_node)

    # 入口：从 supervisor 开始
    workflow.set_entry_point("supervisor")

    # supervisor 根据 state['next'] 条件路由
    workflow.add_conditional_edges(
        "supervisor",
        route_supervisor,
        {
            "data_agent": "data_agent",
            "analyst_agent": "analyst_agent",
            "writer_agent": "writer_agent",
            END: END,
        },
    )

    # 每个 Agent 完成后回到 supervisor 重新决策
    workflow.add_edge("data_agent", "supervisor")
    workflow.add_edge("analyst_agent", "supervisor")
    workflow.add_edge("writer_agent", "supervisor")

    return workflow.compile()
