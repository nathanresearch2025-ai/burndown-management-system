import logging                          # 等价 Java：import org.slf4j.LoggerFactory;
from langgraph.graph import StateGraph, END   # 等价 Java：import com.langgraph.StateGraph; import com.langgraph.End;（LangGraph 图构建类和终止标记）

from .state import StandupV2State      # 等价 Java：import com.burndown.graph_v2.StandupV2State;
from .nodes import (                   # 等价 Java：import com.burndown.graph_v2.Nodes.*;（导入四个节点处理函数）
    supervisor_node,                   # 等价 Java：import com.burndown.graph_v2.Nodes.supervisorNode;
    data_agent_node,                   # 等价 Java：import com.burndown.graph_v2.Nodes.dataAgentNode;
    analyst_agent_node,                # 等价 Java：import com.burndown.graph_v2.Nodes.analystAgentNode;
    writer_agent_node,                 # 等价 Java：import com.burndown.graph_v2.Nodes.writerAgentNode;
)

# 等价 Java：private static final Logger logger = LoggerFactory.getLogger(GraphBuilder.class);
logger = logging.getLogger(__name__)


# ── 条件路由：Supervisor 决策 → 对应 Agent ────────────────────────────────────

# 等价 Java：public String routeSupervisor(StandupV2State state)
def route_supervisor(state: StandupV2State) -> str:
    """读取 Supervisor 写入 state['next']，路由到对应 Agent 节点或 END。"""
    # 等价 Java：String nextAgent = state.getOrDefault("next", "data_agent");
    next_agent = state.get("next", "data_agent")
    # 等价 Java：logger.info("[Route] supervisor → {}", nextAgent);
    logger.info(f"[Route] supervisor → {next_agent}")
    # 等价 Java：if ("FINISH".equals(nextAgent)) return END;
    if next_agent == "FINISH":
        return END   # 等价 Java：return END; （终止图执行）
    # 等价 Java：return nextAgent;
    return next_agent


# ── 图构建 ────────────────────────────────────────────────────────────────────

# 等价 Java：public CompiledGraph buildGraphV2()
def build_graph_v2():
    """构建多 Agent + ReAct 完整版 LangGraph 图。

    拓扑结构：
        supervisor → data_agent (ReAct 循环) → supervisor
        supervisor → analyst_agent → supervisor
        supervisor → writer_agent → supervisor
        supervisor → END
    """
    # 等价 Java：StateGraph<StandupV2State> workflow = new StateGraph<>(StandupV2State.class);
    workflow = StateGraph(StandupV2State)

    # 注册节点 —— 等价 Java：workflow.addNode("supervisor", Nodes::supervisorNode);
    workflow.add_node("supervisor", supervisor_node)       # 等价 Java：workflow.addNode("supervisor",   supervisorNode);
    workflow.add_node("data_agent", data_agent_node)       # 等价 Java：workflow.addNode("data_agent",   dataAgentNode);
    workflow.add_node("analyst_agent", analyst_agent_node) # 等价 Java：workflow.addNode("analyst_agent", analystAgentNode);
    workflow.add_node("writer_agent", writer_agent_node)   # 等价 Java：workflow.addNode("writer_agent",  writerAgentNode);

    # 入口：从 supervisor 开始 —— 等价 Java：workflow.setEntryPoint("supervisor");
    workflow.set_entry_point("supervisor")   # 等价 Java：workflow.setEntryPoint("supervisor");

    # supervisor 根据 state['next'] 条件路由
    # 等价 Java：workflow.addConditionalEdges("supervisor", this::routeSupervisor, Map.of(...));
    workflow.add_conditional_edges(
        "supervisor",        # 等价 Java：源节点名称
        #函数名作为参数传入函数中，route_supervisor主要是在supervisor_node执行完后，route_supervisor函数进行下一步的完成；
        route_supervisor,    # 等价 Java：路由函数引用 this::routeSupervisor
        # route_supervisor 返回 "data_agent" → 走 data_agent 节点
        {                    # 等价 Java：Map.of("data_agent","data_agent", "analyst_agent","analyst_agent", ...)

            "data_agent":    "data_agent",      # 等价 Java："data_agent"    -> "data_agent"
            "analyst_agent": "analyst_agent",  # 等价 Java："analyst_agent" -> "analyst_agent"
            "writer_agent":  "writer_agent",   # 等价 Java："writer_agent"  -> "writer_agent"
            END:             END,               # 等价 Java：END -> END（终止）
        },
    )

    # 每个 Agent 完成后回到 supervisor 重新决策
    workflow.add_edge("data_agent",    "supervisor")   # 等价 Java：workflow.addEdge("data_agent",    "supervisor");
    workflow.add_edge("analyst_agent", "supervisor")   # 等价 Java：workflow.addEdge("analyst_agent", "supervisor");
    workflow.add_edge("writer_agent",  "supervisor")   # 等价 Java：workflow.addEdge("writer_agent",  "supervisor");

    # 编译图并返回可执行对象 —— 等价 Java：return workflow.compile();
    return workflow.compile()   # 等价 Java：return workflow.compile();（生成可 invoke 的 CompiledGraph 实例）
