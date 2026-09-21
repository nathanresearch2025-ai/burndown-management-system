import asyncio
import json
import logging
import re
import time
from typing import Optional

from ..llm import build_llm
from .. import tools as backend_tools
from .state import StandupGraphState

logger = logging.getLogger(__name__)

SYSTEM_SUMMARIZER = (
    "你是 Scrum 站会助手。你会收到：用户问题 + 三个工具的原始输出（字符串）。\n"
    "请严格输出一个 JSON 对象，字段如下：\n"
    '{\n'
    '  "answer": "中文最终回答（结论 + 证据 + 建议）",\n'
    '  "toolsUsed": ["getInProgressTasks","getSprintBurndown","evaluateBurndownRisk"],\n'
    '  "evidence": ["从工具输出中摘取的关键证据行/关键数值，最多 6 条"],\n'
    '  "riskLevel": "LOW|MEDIUM|HIGH|UNKNOWN"\n'
    "}\n"
    "约束：\n"
    "- 不要编造工具输出中不存在的数据；\n"
    "- 如果 sprintId 为空或燃尽数据缺失，riskLevel 输出 UNKNOWN，并在 answer 里说明原因；\n"
    "- 如果某个工具数据获取失败，在 answer 中注明该数据暂不可用；\n"
    "- JSON 必须可解析，不要额外输出解释文本。"
)

# ── 节点 1：输入校验 ────────────────────────────────────────────────────────

async def validate_input(state: StandupGraphState) -> StandupGraphState:
    """校验必要输入字段，sprint_id 缺失时直接标记，避免后续工具白调。"""
    logger.info("[Node] validate_input start")
    state["tool_errors"] = []
    state["missing_fields"] = []
    state["retry_count"] = 0
    state["enriched"] = False
    state["in_progress_raw"] = None
    state["burndown_raw"] = None
    state["risk_raw"] = None
    state["answer"] = ""
    state["tools_used"] = []
    state["evidence"] = []
    state["risk_level"] = "UNKNOWN"

    if not state.get("sprint_id"):
        state["missing_fields"].append("sprint_id")
        logger.info("[Node] validate_input: sprint_id missing, will short-circuit burndown tools")

    logger.info("[Node] validate_input done")
    return state


# ── 节点 2：并行工具调用 ─────────────────────────────────────────────────────

async def fetch_core_tools(state: StandupGraphState) -> StandupGraphState:
    """并行调用三个后端工具，独立捕获每个工具的异常，不因单点失败终止整图。"""
    logger.info("[Node] fetch_core_tools start")
    t0 = time.time()

    project_id = state["project_id"]
    sprint_id = state.get("sprint_id") or 0
    user_id = state["user_id"]
    trace_id = state.get("trace_id")
    skip_sprint = "sprint_id" in state.get("missing_fields", [])

    async def safe_call(coro, tool_name: str) -> tuple[str, Optional[str]]:
        try:
            result = await coro
            return tool_name, result
        except Exception as e:
            logger.warning(f"[Node] fetch_core_tools: {tool_name} failed: {e}")
            return tool_name, None

    async def noop(msg: str) -> str:
        return msg

    tasks = [
        safe_call(
            backend_tools.get_in_progress_tasks(project_id, user_id, trace_id=trace_id),
            "getInProgressTasks",
        ),
        safe_call(
            backend_tools.get_sprint_burndown(project_id, sprint_id, trace_id=trace_id)
            if not skip_sprint else noop("sprintId未提供，跳过燃尽查询"),
            "getSprintBurndown",
        ),
        safe_call(
            backend_tools.evaluate_burndown_risk(project_id, sprint_id, trace_id=trace_id)
            if not skip_sprint else noop("sprintId未提供，跳过风险评估"),
            "evaluateBurndownRisk",
        ),
    ]

    results = await asyncio.gather(*tasks)

    tool_errors = list(state.get("tool_errors", []))
    tools_used = []
    for tool_name, output in results:
        if output is None:
            tool_errors.append(tool_name)
        else:
            tools_used.append(tool_name)
            if tool_name == "getInProgressTasks":
                state["in_progress_raw"] = output
            elif tool_name == "getSprintBurndown":
                state["burndown_raw"] = output
            elif tool_name == "evaluateBurndownRisk":
                state["risk_raw"] = output

    state["tool_errors"] = tool_errors
    state["tools_used"] = tools_used
    logger.info(f"[Node] fetch_core_tools done in {int((time.time()-t0)*1000)}ms, errors={tool_errors}")
    return state

# ── 节点 3：数据完整性校验 ──────────────────────────────────────────────────

async def validate_data(state: StandupGraphState) -> StandupGraphState:
    """检查工具输出是否足够，标记缺失字段供后续节点路由判断。"""
    logger.info("[Node] validate_data start")
    missing = list(state.get("missing_fields", []))

    burndown = state.get("burndown_raw") or ""
    if not burndown.strip() or burndown.strip() in ("null", "[]", "{}"):
        if "burndown" not in missing:
            missing.append("burndown")

    state["missing_fields"] = missing
    logger.info(f"[Node] validate_data: tool_errors={state['tool_errors']}, missing={missing}")
    return state


# ── 节点 4：重试失败工具（最多 1 次）────────────────────────────────────────

async def retry_tools(state: StandupGraphState) -> StandupGraphState:
    """对上一轮失败的工具重试一次，成功则更新对应字段，失败则保留 tool_errors 标记。"""
    logger.info("[Node] retry_tools start")
    errors = list(state.get("tool_errors", []))
    project_id = state["project_id"]
    sprint_id = state.get("sprint_id") or 0
    user_id = state["user_id"]
    trace_id = state.get("trace_id")
    still_failing = []

    for tool_name in errors:
        try:
            if tool_name == "getInProgressTasks":
                state["in_progress_raw"] = await backend_tools.get_in_progress_tasks(
                    project_id, user_id, trace_id=trace_id
                )
            elif tool_name == "getSprintBurndown":
                state["burndown_raw"] = await backend_tools.get_sprint_burndown(
                    project_id, sprint_id, trace_id=trace_id
                )
            elif tool_name == "evaluateBurndownRisk":
                state["risk_raw"] = await backend_tools.evaluate_burndown_risk(
                    project_id, sprint_id, trace_id=trace_id
                )
            logger.info(f"[Node] retry_tools: {tool_name} succeeded on retry")
        except Exception as e:
            logger.warning(f"[Node] retry_tools: {tool_name} still failing: {e}")
            still_failing.append(tool_name)

    state["tool_errors"] = still_failing
    state["retry_count"] = state.get("retry_count", 0) + 1
    return state


# ── 节点 5：补充查询（burndown 为空时）──────────────────────────────────────

async def enrich_data(state: StandupGraphState) -> StandupGraphState:
    """burndown 数据为空时，补充一段说明文字作为占位，避免 LLM 收到空字段。"""
    logger.info("[Node] enrich_data start")
    if not state.get("burndown_raw"):
        state["burndown_raw"] = "燃尽数据暂无记录（Sprint 可能刚创建或尚未产生数据点）"
    state["enriched"] = True
    logger.info("[Node] enrich_data done")
    return state

# ── 节点 6：LLM 汇总 ────────────────────────────────────────────────────────

async def summarize(state: StandupGraphState) -> StandupGraphState:
    """将工具输出交给 LLM，生成结构化 JSON 回答。"""
    logger.info("[Node] summarize start")
    t0 = time.time()

    error_note = ""
    if state.get("tool_errors"):
        error_note = f"\n注意：以下工具数据获取失败，请在回答中说明：{state['tool_errors']}"

    user_content = (
        f"问题：{state['question']}{error_note}\n"
        f"getInProgressTasks 输出：{state.get('in_progress_raw') or '无数据'}\n"
        f"getSprintBurndown 输出：{state.get('burndown_raw') or '无数据'}\n"
        f"evaluateBurndownRisk 输出：{state.get('risk_raw') or '无数据'}"
    )

    from langchain_core.messages import HumanMessage, SystemMessage
    llm = build_llm()
    response = await llm.ainvoke([
        SystemMessage(content=SYSTEM_SUMMARIZER),
        HumanMessage(content=user_content),
    ])
    state["_llm_raw"] = response.content
    logger.info(f"[Node] summarize done in {int((time.time()-t0)*1000)}ms")
    return state


# ── 节点 7：兜底解析 ─────────────────────────────────────────────────────────

async def finalize(state: StandupGraphState) -> StandupGraphState:
    """解析 LLM 输出的 JSON；若解析失败则用结构化 fallback 而非抛异常。"""
    logger.info("[Node] finalize start")
    raw = state.get("_llm_raw", "")

    parsed = None
    # 尝试直接解析
    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError:
        # 提取 ```json ... ``` 或第一个 { ... } 块
        m = re.search(r"```json\s*([\s\S]*?)```", raw)
        if m:
            try:
                parsed = json.loads(m.group(1))
            except json.JSONDecodeError:
                pass
        if parsed is None:
            m2 = re.search(r"\{[\s\S]*\}", raw)
            if m2:
                try:
                    parsed = json.loads(m2.group())
                except json.JSONDecodeError:
                    pass

    if parsed:
        state["answer"] = parsed.get("answer", raw)
        state["tools_used"] = parsed.get("toolsUsed", state.get("tools_used", []))
        state["evidence"] = parsed.get("evidence", [])
        state["risk_level"] = parsed.get("riskLevel", "UNKNOWN")
    else:
        logger.warning("[Node] finalize: JSON parse failed, using raw as answer")
        state["answer"] = raw or "抱歉，暂时无法生成站会摘要，请稍后重试。"
        state["risk_level"] = "UNKNOWN"

    # 清理内部字段
    state.pop("_llm_raw", None)
    logger.info(f"[Node] finalize done, risk_level={state['risk_level']}")
    return state


# ── 节点 8：快速退出（sprint_id 缺失时）────────────────────────────────────

async def early_exit(state: StandupGraphState) -> StandupGraphState:
    """sprintId 完全缺失时，直接输出说明，跳过所有工具和 LLM 调用。"""
    logger.info("[Node] early_exit triggered")
    # 仍然调用 getInProgressTasks，因为它不需要 sprintId
    try:
        state["in_progress_raw"] = await backend_tools.get_in_progress_tasks(
            state["project_id"], state["user_id"], trace_id=state.get("trace_id")
        )
        state["tools_used"] = ["getInProgressTasks"]
    except Exception as e:
        logger.warning(f"[Node] early_exit: getInProgressTasks failed: {e}")
        state["in_progress_raw"] = None
        state["tools_used"] = []

    task_info = state.get("in_progress_raw") or "无法获取进行中任务信息"
    state["answer"] = (
        f"未提供 sprintId，无法查询燃尽图和风险数据。\n"
        f"当前进行中任务信息：{task_info}"
    )
    state["evidence"] = []
    state["risk_level"] = "UNKNOWN"
    return state



