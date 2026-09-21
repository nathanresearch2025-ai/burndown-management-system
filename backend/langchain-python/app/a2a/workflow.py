import logging
from typing import Dict, Any
from .data_agent import DataAgentA2A
from .analyst_agent import AnalystAgentA2A

logger = logging.getLogger(__name__)


async def run_a2a_workflow(
    question: str,
    project_id: int,
    sprint_id: int,
    user_id: int,
    trace_id: str = None,
    force_a2a: bool = False,  # 强制触发 A2A（测试用）
) -> Dict[str, Any]:
    """A2A 工作流：DataAgent 和 AnalystAgent 支持相互调用

    场景1：DataAgent 检测燃尽图异常 → 请求 AnalystAgent 验证
    场景2：AnalystAgent 数据不足 → 请求 DataAgent 补充
    """
    logger.info(f"[A2A Workflow] Starting - question: {question}")

    # 初始化 Agent
    data_agent = DataAgentA2A()
    analyst_agent = AnalystAgentA2A()

    # 相互注册（支持 A2A 调用）
    data_agent.register_agent("analyst", analyst_agent)
    analyst_agent.register_agent("data", data_agent)

    # 初始 state
    state = {
        "question": question,
        "project_id": project_id,
        "sprint_id": sprint_id,
        "user_id": user_id,
        "trace_id": trace_id,
        "a2a_trace": [],
        "force_a2a": force_a2a,
    }

    # Step 1: DataAgent 收集数据（可能触发 A2A 验证）
    state = await data_agent.execute(state)

    # Step 2: AnalystAgent 分析（可能触发 A2A 补充数据）
    state = await analyst_agent.execute(state)

    # 返回结果
    return {
        "answer": state.get("conclusion", state.get("data_summary", "")),
        "risk_level": state.get("risk_level", "UNKNOWN"),
        "tools_used": state.get("tools_used", []),
        "a2a_trace": state.get("a2a_trace", []),
        "data_summary": state.get("data_summary", ""),
    }
