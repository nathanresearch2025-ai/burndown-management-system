import json
import logging
import re
import time
from langchain_core.messages import HumanMessage, SystemMessage, AIMessage
from langgraph.prebuilt import create_react_agent

from ..llm import build_llm
from .state import StandupV2State
from .prompts import (
    SUPERVISOR_SYSTEM, SUPERVISOR_ROUTE_PROMPT,
    DATA_AGENT_SYSTEM, ANALYST_SYSTEM, WRITER_SYSTEM,
)
from .tools import DATA_AGENT_TOOLS, set_tool_context

logger = logging.getLogger(__name__)


# ── 工具函数 ──────────────────────────────────────────────────────────────────

def _parse_json_safe(text: str) -> dict | None:
    """从 LLM 输出中提取 JSON，兼容 ```json``` 包裹格式。"""
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass
    m = re.search(r"```json\s*([\s\S]*?)```", text)
    if m:
        try:
            return json.loads(m.group(1))
        except json.JSONDecodeError:
            pass
    m2 = re.search(r"\{[\s\S]*\}", text)
    if m2:
        try:
            return json.loads(m2.group())
        except json.JSONDecodeError:
            pass
    return None


# ── 节点 1：Supervisor ────────────────────────────────────────────────────────

async def supervisor_node(state: StandupV2State) -> StandupV2State:
    """Supervisor：LLM 根据当前状态决定下一个执行的 Agent。
    按照 data_agent → analyst_agent → writer_agent → FINISH 顺序编排。
    """
    logger.info("[Supervisor] deciding next agent...")
    llm = build_llm()

    # 构建上下文消息，告知 Supervisor 当前完成情况
    status_lines = []
    if state.get("data_summary"):
        status_lines.append(f"data_agent 已完成：{state['data_summary'][:100]}...")
    if state.get("analysis"):
        status_lines.append(f"analyst_agent 已完成：风险等级={state.get('risk_level')}")
    if state.get("answer"):
        status_lines.append("writer_agent 已完成")

    status_text = "\n".join(status_lines) if status_lines else "尚未执行任何 Agent"

    messages = [
        SystemMessage(content=SUPERVISOR_SYSTEM),
        HumanMessage(content=f"用户问题：{state['question']}\n\n当前状态：\n{status_text}\n\n{SUPERVISOR_ROUTE_PROMPT}"),
    ]

    response = await llm.ainvoke(messages)
    raw = response.content.strip().lower()

    # 提取路由决策
    if "finish" in raw:
        next_agent = "FINISH"
    elif "writer" in raw:
        next_agent = "writer_agent"
    elif "analyst" in raw:
        next_agent = "analyst_agent"
    else:
        next_agent = "data_agent"

    logger.info(f"[Supervisor] next={next_agent}")
    state["next"] = next_agent
    return state


# ── 节点 2：DataAgent（ReAct 循环）──────────────────────────────────────────

async def data_agent_node(state: StandupV2State) -> StandupV2State:
    """DataAgent：使用 LangGraph prebuilt ReAct Agent，LLM 自主决定调用哪些工具及调用顺序。
    工具调用通过 set_tool_context 注入上下文参数。
    """
    logger.info("[DataAgent] ReAct loop start")
    t0 = time.time()

    # 注入工具上下文（project_id, sprint_id, user_id, trace_id）
    set_tool_context(
        project_id=state["project_id"],
        sprint_id=state.get("sprint_id") or 0,
        user_id=state["user_id"],
        trace_id=state.get("trace_id"),
    )

    llm = build_llm()

    # 构建 ReAct Agent（LangGraph prebuilt）
    react_agent = create_react_agent(
        model=llm,
        tools=DATA_AGENT_TOOLS,
        state_modifier=DATA_AGENT_SYSTEM,
    )

    sprint_hint = f"sprint_id={state.get('sprint_id')}" if state.get("sprint_id") else "sprint_id 未提供，仅查进行中任务"
    user_msg = (
        f"请收集以下站会问题所需的所有数据：\n"
        f"问题：{state['question']}\n"
        f"project_id={state['project_id']}, {sprint_hint}, user_id={state['user_id']}\n"
        f"请调用所有相关工具，然后输出一段数据整理摘要。"
    )

    result = await react_agent.ainvoke({"messages": [HumanMessage(content=user_msg)]})

    # 提取 ReAct 消息历史
    react_messages = result.get("messages", [])
    final_answer = ""
    tools_used = []

    for msg in react_messages:
        # 收集工具调用名称
        if hasattr(msg, "tool_calls") and msg.tool_calls:
            for tc in msg.tool_calls:
                name = tc.get("name", "") if isinstance(tc, dict) else getattr(tc, "name", "")
                if name and name not in tools_used:
                    tools_used.append(name)
        # 最后一条 AI 消息为 DataAgent 的数据摘要
        if isinstance(msg, AIMessage) and not getattr(msg, "tool_calls", None):
            final_answer = msg.content

    # 将工具名映射为标准名称
    tool_name_map = {
        "get_in_progress_tasks": "getInProgressTasks",
        "get_sprint_burndown": "getSprintBurndown",
        "evaluate_burndown_risk": "evaluateBurndownRisk",
    }
    state["tools_used"] = [tool_name_map.get(t, t) for t in tools_used]
    state["data_summary"] = final_answer
    state["react_messages"] = [m.content if hasattr(m, "content") else str(m) for m in react_messages]

    logger.info(f"[DataAgent] done in {int((time.time()-t0)*1000)}ms, tools_used={state['tools_used']}")
    return state

# ── 节点 3：AnalystAgent ─────────────────────────────────────────────────────

async def analyst_agent_node(state: StandupV2State) -> StandupV2State:
    """AnalystAgent：基于 DataAgent 输出的数据摘要进行风险分析。
    输出结构化 JSON：risk_level + analysis + key_findings。
    """
    logger.info("[AnalystAgent] start")
    t0 = time.time()
    llm = build_llm()

    data_summary = state.get("data_summary") or "无数据"
    messages = [
        SystemMessage(content=ANALYST_SYSTEM),
        HumanMessage(content=f"以下是 DataAgent 收集到的原始数据摘要：\n\n{data_summary}\n\n请进行风险分析并输出 JSON。"),
    ]

    response = await llm.ainvoke(messages)
    parsed = _parse_json_safe(response.content)

    if parsed:
        state["risk_level"] = parsed.get("risk_level", "UNKNOWN")
        findings = parsed.get("key_findings", [])
        state["analysis"] = parsed.get("analysis", response.content)
        # 将 key_findings 存入 evidence 供 WriterAgent 使用
        state["evidence"] = findings[:6]
    else:
        logger.warning("[AnalystAgent] JSON parse failed, using raw")
        state["risk_level"] = "UNKNOWN"
        state["analysis"] = response.content
        state["evidence"] = []

    logger.info(f"[AnalystAgent] done in {int((time.time()-t0)*1000)}ms, risk_level={state['risk_level']}")
    return state


# ── 节点 4：WriterAgent ───────────────────────────────────────────────────────

async def writer_agent_node(state: StandupV2State) -> StandupV2State:
    """WriterAgent：整合所有数据和分析，生成面向团队的最终站会报告。
    输出结构化 JSON：answer + evidence。
    """
    logger.info("[WriterAgent] start")
    t0 = time.time()
    llm = build_llm()

    context = (
        f"用户问题：{state['question']}\n\n"
        f"=== DataAgent 数据摘要 ===\n{state.get('data_summary') or '无数据'}\n\n"
        f"=== AnalystAgent 风险分析 ===\n"
        f"风险等级：{state.get('risk_level', 'UNKNOWN')}\n"
        f"分析结论：{state.get('analysis') or '无分析'}\n\n"
        f"请生成最终站会报告 JSON。"
    )

    messages = [
        SystemMessage(content=WRITER_SYSTEM),
        HumanMessage(content=context),
    ]

    response = await llm.ainvoke(messages)
    parsed = _parse_json_safe(response.content)

    if parsed:
        state["answer"] = parsed.get("answer", response.content)
        writer_evidence = parsed.get("evidence", [])
        # 合并 AnalystAgent 的 key_findings 与 WriterAgent 的 evidence
        combined = list(dict.fromkeys(state.get("evidence", []) + writer_evidence))
        state["evidence"] = combined[:6]
    else:
        logger.warning("[WriterAgent] JSON parse failed, using raw")
        state["answer"] = response.content

    logger.info(f"[WriterAgent] done in {int((time.time()-t0)*1000)}ms")
    return state

