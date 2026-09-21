import json                              # 等价 Java：import com.fasterxml.jackson.databind.ObjectMapper;
import logging                           # 等价 Java：import org.slf4j.LoggerFactory;
import re                                # 等价 Java：import java.util.regex.Pattern; import java.util.regex.Matcher;
import time                              # 等价 Java：import java.time.Instant;（用于计算耗时）
from langchain_core.messages import HumanMessage, SystemMessage, AIMessage   # 等价 Java：import com.langchain.messages.*; （LLM 消息类型）
from langgraph.prebuilt import create_react_agent   # 等价 Java：import com.langgraph.prebuilt.ReactAgentFactory;（prebuilt ReAct Agent 工厂）

from ..llm import build_llm              # 等价 Java：import com.burndown.llm.LlmFactory;
from .state import StandupV2State       # 等价 Java：import com.burndown.graph_v2.StandupV2State;
from .prompts import (                  # 等价 Java：import com.burndown.graph_v2.Prompts.*;（导入所有提示词常量）
    SUPERVISOR_SYSTEM, SUPERVISOR_ROUTE_PROMPT,   # 等价 Java：Prompts.SUPERVISOR_SYSTEM, Prompts.SUPERVISOR_ROUTE_PROMPT
    DATA_AGENT_SYSTEM, ANALYST_SYSTEM, WRITER_SYSTEM,   # 等价 Java：Prompts.DATA_AGENT_SYSTEM, Prompts.ANALYST_SYSTEM, Prompts.WRITER_SYSTEM
)
from .tools import DATA_AGENT_TOOLS, set_tool_context   # 等价 Java：import com.burndown.graph_v2.Tools;

# 等价 Java：private static final Logger logger = LoggerFactory.getLogger(Nodes.class);
logger = logging.getLogger(__name__)


# ── 工具函数 ──────────────────────────────────────────────────────────────────

# 等价 Java：@Nullable private Map<String,Object> parseJsonSafe(String text)
def _parse_json_safe(text: str) -> dict | None:
    """从 LLM 输出中提取 JSON，兼容 ```json``` 包裹格式。"""
    # 等价 Java：try { return objectMapper.readValue(text, Map.class); } catch (JsonProcessingException e) { /* 继续尝试 */ }
    try:
        return json.loads(text)   # 等价 Java：return objectMapper.readValue(text, Map.class);
    except json.JSONDecodeError:  # 等价 Java：catch (JsonProcessingException e)
        pass                      # 等价 Java：// 直接解析失败，尝试提取 ```json``` 块
    # 等价 Java：Matcher m = Pattern.compile("```json\\s*([\\s\\S]*?)```").matcher(text);
    m = re.search(r"```json\s*([\s\S]*?)```", text)
    # 等价 Java：if (m.find()) { try { return objectMapper.readValue(m.group(1), Map.class); } catch (...) {} }
    if m:
        try:
            return json.loads(m.group(1))   # 等价 Java：return objectMapper.readValue(m.group(1), Map.class);
        except json.JSONDecodeError:        # 等价 Java：catch (JsonProcessingException e)
            pass                            # 等价 Java：// 继续尝试裸 JSON
    # 等价 Java：Matcher m2 = Pattern.compile("\\{[\\s\\S]*\\}").matcher(text);
    m2 = re.search(r"\{[\s\S]*\}", text)
    # 等价 Java：if (m2.find()) { try { return objectMapper.readValue(m2.group(), Map.class); } catch (...) {} }
    if m2:
        try:
            return json.loads(m2.group())   # 等价 Java：return objectMapper.readValue(m2.group(), Map.class);
        except json.JSONDecodeError:        # 等价 Java：catch (JsonProcessingException e)
            pass                            # 等价 Java：// 所有尝试均失败
    # 等价 Java：return null;
    return None


# ── 节点 1：Supervisor ────────────────────────────────────────────────────────

# 等价 Java：public StandupV2State supervisorNode(StandupV2State state)
async def supervisor_node(state: StandupV2State) -> StandupV2State:
    """Supervisor：LLM 根据当前状态决定下一个 Agent。
    按照 data_agent → analyst_agent → writer_agent → FINISH 顺序编排。
    """
    # 等价 Java：logger.info("[Supervisor] deciding next agent...");
    logger.info("[Supervisor] deciding next agent...")
    # 等价 Java：ChatModel llm = LlmFactory.buildLlm();
    llm = build_llm()

    # 构建上下文消息，告知 Supervisor 当前完成情况
    # 等价 Java：List<String> statusLines = new ArrayList<>();
    status_lines = []
    # 等价 Java：if (state.getDataSummary() != null) statusLines.add("data_agent 已完成：" + state.getDataSummary().substring(0,100) + "...");
    if state.get("data_summary"):
        status_lines.append(f"data_agent 已完成：{state['data_summary'][:100]}...")
    # 等价 Java：if (state.getAnalysis() != null) statusLines.add("analyst_agent 已完成：风险等级=" + state.getRiskLevel());
    if state.get("analysis"):
        status_lines.append(f"analyst_agent 已完成：风险等级={state.get('risk_level')}")
    # 等价 Java：if (state.getAnswer() != null) statusLines.add("writer_agent 已完成");
    if state.get("answer"):
        status_lines.append("writer_agent 已完成")

    # 等价 Java：String statusText = statusLines.isEmpty() ? "尚未执行任何 Agent" : String.join("\n", statusLines);
    status_text = "\n".join(status_lines) if status_lines else "尚未执行任何 Agent"

    # 等价 Java：List<Message> messages = List.of(new SystemMessage(SUPERVISOR_SYSTEM), new HumanMessage(...));
    messages = [
        SystemMessage(content=SUPERVISOR_SYSTEM),   # 等价 Java：new SystemMessage(Prompts.SUPERVISOR_SYSTEM)
        HumanMessage(content=f"用户问题：{state['question']}\n\n当前状态：\n{status_text}\n\n{SUPERVISOR_ROUTE_PROMPT}"),   # 等价 Java：new HumanMessage(...)
    ]

    # 等价 Java：AiResponse response = llm.invoke(messages);
    response = await llm.ainvoke(messages)
    # 等价 Java：String raw = response.getContent().strip().toLowerCase();
    raw = response.content.strip().lower()

    # 提取路由决策 —— 等价 Java：String nextAgent;
    # 等价 Java：if (raw.contains("finish")) nextAgent = "FINISH";
    if "finish" in raw:
        next_agent = "FINISH"
    # 等价 Java：else if (raw.contains("writer")) nextAgent = "writer_agent";
    elif "writer" in raw:
        next_agent = "writer_agent"
    # 等价 Java：else if (raw.contains("analyst")) nextAgent = "analyst_agent";
    elif "analyst" in raw:
        next_agent = "analyst_agent"
    # 等价 Java：else nextAgent = "data_agent";
    else:
        next_agent = "data_agent"

    # 等价 Java：logger.info("[Supervisor] next={}", nextAgent);
    logger.info(f"[Supervisor] next={next_agent}")
    # 等价 Java：state.setNext(nextAgent); return state;
    state["next"] = next_agent
    return state


# ── 节点 2：DataAgent（ReAct 循环）──────────────────────────────────────────

# 等价 Java：public StandupV2State dataAgentNode(StandupV2State state)
async def data_agent_node(state: StandupV2State) -> StandupV2State:
    """DataAgent：使用 LangGraph prebuilt ReAct Agent，LLM 自主决定调用哪些工具及调用顺序。
    工具调用通过 set_tool_context 注入上下文参数。
    """
    # 等价 Java：logger.info("[DataAgent] ReAct loop start");
    logger.info("[DataAgent] ReAct loop start")
    # 等价 Java：long t0 = System.currentTimeMillis();
    t0 = time.time()

    # 注入工具上下文（project_id, sprint_id, user_id, trace_id）
    # 等价 Java：Tools.setToolContext(state.getProjectId(), state.getSprintId() != null ? state.getSprintId() : 0, state.getUserId(), state.getTraceId());
    set_tool_context(
        project_id=state["project_id"],                  # 等价 Java：state.getProjectId()
        sprint_id=state.get("sprint_id") or 0,           # 等价 Java：state.getSprintId() != null ? state.getSprintId() : 0
        user_id=state["user_id"],                        # 等价 Java：state.getUserId()
        trace_id=state.get("trace_id"),                  # 等价 Java：state.getTraceId()  // @Nullable
    )

    # 等价 Java：ChatModel llm = LlmFactory.buildLlm();
    llm = build_llm()

    # 构建 ReAct Agent（LangGraph prebuilt）
    # 等价 Java：ReactAgent reactAgent = ReactAgentFactory.create(llm, DATA_AGENT_TOOLS, DATA_AGENT_SYSTEM);
    react_agent = create_react_agent(
        model=llm,                        # 等价 Java：llm（语言模型实例）
        tools=DATA_AGENT_TOOLS,           # 等价 Java：Tools.DATA_AGENT_TOOLS（可用工具列表）
        state_modifier=DATA_AGENT_SYSTEM, # 等价 Java：系统提示词，注入到 Agent 的 SystemMessage
    )

    # 等价 Java：String sprintHint = state.getSprintId() != null ? "sprint_id=" + state.getSprintId() : "sprint_id 未提供，仅查进行中任务";
    sprint_hint = f"sprint_id={state.get('sprint_id')}" if state.get("sprint_id") else "sprint_id 未提供，仅查进行中任务"
    # 等价 Java：String userMsg = "请收集以下站会问题所需的所有数据：\n" + ...;
    user_msg = (
        f"请收集以下站会问题所需的所有数据：\n"
        f"问题：{state['question']}\n"
        f"project_id={state['project_id']}, {sprint_hint}, user_id={state['user_id']}\n"
        f"请调用所有相关工具，然后输出一段数据整理摘要。"
    )

    # 等价 Java：Map<String,Object> result = reactAgent.invoke(Map.of("messages", List.of(new HumanMessage(userMsg))));
    result = await react_agent.ainvoke({"messages": [HumanMessage(content=user_msg)]})

    # 提取 ReAct 消息历史
    # 等价 Java：List<Message> reactMessages = (List<Message>) result.get("messages");
    react_messages = result.get("messages", [])
    # 等价 Java：String finalAnswer = "";
    final_answer = ""
    # 等价 Java：List<String> toolsUsed = new ArrayList<>();
    tools_used = []

    # 等价 Java：for (Message msg : reactMessages) {
    for msg in react_messages:
        # 收集工具调用名称
        # 等价 Java：if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) { for (ToolCall tc : msg.getToolCalls()) { ... } }
        if hasattr(msg, "tool_calls") and msg.tool_calls:
            for tc in msg.tool_calls:   # 等价 Java：for (ToolCall tc : msg.getToolCalls())
                # 等价 Java：String name = tc instanceof Map ? (String)((Map)tc).get("name") : tc.getName();
                name = tc.get("name", "") if isinstance(tc, dict) else getattr(tc, "name", "")
                # 等价 Java：if (name != null && !toolsUsed.contains(name)) toolsUsed.add(name);
                if name and name not in tools_used:
                    tools_used.append(name)
        # 最后一条 AI 消息为 DataAgent 的数据摘要
        # 等价 Java：if (msg instanceof AiMessage && msg.getToolCalls() == null) finalAnswer = msg.getContent();
        if isinstance(msg, AIMessage) and not getattr(msg, "tool_calls", None):
            final_answer = msg.content   # 等价 Java：finalAnswer = msg.getContent();

    # 将工具名映射为标准名称
    # 等价 Java：Map<String,String> toolNameMap = Map.of("get_in_progress_tasks","getInProgressTasks", ...);
    tool_name_map = {
        "get_in_progress_tasks": "getInProgressTasks",    # 等价 Java："get_in_progress_tasks" -> "getInProgressTasks"
        "get_sprint_burndown":   "getSprintBurndown",     # 等价 Java："get_sprint_burndown"   -> "getSprintBurndown"
        "evaluate_burndown_risk": "evaluateBurndownRisk", # 等价 Java："evaluate_burndown_risk"-> "evaluateBurndownRisk"
    }
    # 等价 Java：state.setToolsUsed(toolsUsed.stream().map(t -> toolNameMap.getOrDefault(t,t)).collect(Collectors.toList()));
    state["tools_used"] = [tool_name_map.get(t, t) for t in tools_used]
    # 等价 Java：state.setDataSummary(finalAnswer);
    state["data_summary"] = final_answer
    # 等价 Java：state.setReactMessages(reactMessages.stream().map(m -> m.getContent()).collect(Collectors.toList()));
    state["react_messages"] = [m.content if hasattr(m, "content") else str(m) for m in react_messages]

    # 等价 Java：logger.info("[DataAgent] done in {}ms, tools_used={}", System.currentTimeMillis()-t0, state.getToolsUsed());
    logger.info(f"[DataAgent] done in {int((time.time()-t0)*1000)}ms, tools_used={state['tools_used']}")
    # 等价 Java：return state;
    return state


# ── 节点 3：AnalystAgent ─────────────────────────────────────────────────────

# 等价 Java：public StandupV2State analystAgentNode(StandupV2State state)
async def analyst_agent_node(state: StandupV2State) -> StandupV2State:
    """AnalystAgent：基于 DataAgent 输出的数据摘要进行风险分析。
    输出结构化 JSON：risk_level + analysis + key_findings。
    """
    # 等价 Java：logger.info("[AnalystAgent] start");
    logger.info("[AnalystAgent] start")
    # 等价 Java：long t0 = System.currentTimeMillis();
    t0 = time.time()
    # 等价 Java：ChatModel llm = LlmFactory.buildLlm();
    llm = build_llm()

    # 等价 Java：String dataSummary = state.getDataSummary() != null ? state.getDataSummary() : "无数据";
    data_summary = state.get("data_summary") or "无数据"
    # 等价 Java：List<Message> messages = List.of(new SystemMessage(ANALYST_SYSTEM), new HumanMessage(...));
    messages = [
        SystemMessage(content=ANALYST_SYSTEM),   # 等价 Java：new SystemMessage(Prompts.ANALYST_SYSTEM)
        HumanMessage(content=f"以下是 DataAgent 收集到的原始数据摘要：\n\n{data_summary}\n\n请进行风险分析并输出 JSON。"),   # 等价 Java：new HumanMessage(...)
    ]

    # 等价 Java：AiResponse response = llm.invoke(messages);
    response = await llm.ainvoke(messages)
    # 等价 Java：Map<String,Object> parsed = parseJsonSafe(response.getContent());
    parsed = _parse_json_safe(response.content)

    # 等价 Java：if (parsed != null) {
    if parsed:
        # 等价 Java：state.setRiskLevel((String) parsed.getOrDefault("risk_level", "UNKNOWN"));
        state["risk_level"] = parsed.get("risk_level", "UNKNOWN")
        # 等价 Java：List<String> findings = (List<String>) parsed.getOrDefault("key_findings", List.of());
        findings = parsed.get("key_findings", [])
        # 等价 Java：state.setAnalysis((String) parsed.getOrDefault("analysis", response.getContent()));
        state["analysis"] = parsed.get("analysis", response.content)
        # 将 key_findings 存入 evidence 供 WriterAgent 使用
        # 等价 Java：state.setEvidence(findings.subList(0, Math.min(findings.size(), 6)));
        state["evidence"] = findings[:6]
    # 等价 Java：} else {
    else:
        # 等价 Java：logger.warn("[AnalystAgent] JSON parse failed, using raw");
        logger.warning("[AnalystAgent] JSON parse failed, using raw")
        # 等价 Java：state.setRiskLevel("UNKNOWN");
        state["risk_level"] = "UNKNOWN"
        # 等价 Java：state.setAnalysis(response.getContent());
        state["analysis"] = response.content
        # 等价 Java：state.setEvidence(Collections.emptyList());
        state["evidence"] = []

    # 等价 Java：logger.info("[AnalystAgent] done in {}ms, risk_level={}", System.currentTimeMillis()-t0, state.getRiskLevel());
    logger.info(f"[AnalystAgent] done in {int((time.time()-t0)*1000)}ms, risk_level={state['risk_level']}")
    # 等价 Java：return state;
    return state


# ── 节点 4：WriterAgent ───────────────────────────────────────────────────────

# 等价 Java：public StandupV2State writerAgentNode(StandupV2State state)
async def writer_agent_node(state: StandupV2State) -> StandupV2State:
    """WriterAgent：整合所有数据和分析，生成面向团队的最终站会报告。
    输出结构化 JSON：answer + evidence。
    """
    # 等价 Java：logger.info("[WriterAgent] start");
    logger.info("[WriterAgent] start")
    # 等价 Java：long t0 = System.currentTimeMillis();
    t0 = time.time()
    # 等价 Java：ChatModel llm = LlmFactory.buildLlm();
    llm = build_llm()

    # 等价 Java：String context = "用户问题：" + state.getQuestion() + "\n\n" + ...;
    context = (
        f"用户问题：{state['question']}\n\n"
        f"=== DataAgent 数据摘要 ===\n{state.get('data_summary') or '无数据'}\n\n"
        f"=== AnalystAgent 风险分析 ===\n"
        f"风险等级：{state.get('risk_level', 'UNKNOWN')}\n"
        f"分析结论：{state.get('analysis') or '无分析'}\n\n"
        f"请生成最终站会报告 JSON。"
    )

    # 等价 Java：List<Message> messages = List.of(new SystemMessage(WRITER_SYSTEM), new HumanMessage(context));
    messages = [
        SystemMessage(content=WRITER_SYSTEM),    # 等价 Java：new SystemMessage(Prompts.WRITER_SYSTEM)
        HumanMessage(content=context),           # 等价 Java：new HumanMessage(context)
    ]

    # 等价 Java：AiResponse response = llm.invoke(messages);
    response = await llm.ainvoke(messages)
    # 等价 Java：Map<String,Object> parsed = parseJsonSafe(response.getContent());
    parsed = _parse_json_safe(response.content)

    # 等价 Java：if (parsed != null) {
    if parsed:
        # 等价 Java：state.setAnswer((String) parsed.getOrDefault("answer", response.getContent()));
        state["answer"] = parsed.get("answer", response.content)
        # 等价 Java：List<String> writerEvidence = (List<String>) parsed.getOrDefault("evidence", List.of());
        writer_evidence = parsed.get("evidence", [])
        # 合并 AnalystAgent 的 key_findings 与 WriterAgent 的 evidence
        # 等价 Java：List<String> combined = Stream.concat(state.getEvidence().stream(), writerEvidence.stream()).distinct().limit(6).collect(Collectors.toList());
        combined = list(dict.fromkeys(state.get("evidence", []) + writer_evidence))
        # 等价 Java：state.setEvidence(combined.subList(0, Math.min(combined.size(), 6)));
        state["evidence"] = combined[:6]
    # 等价 Java：} else {
    else:
        # 等价 Java：logger.warn("[WriterAgent] JSON parse failed, using raw");
        logger.warning("[WriterAgent] JSON parse failed, using raw")
        # 等价 Java：state.setAnswer(response.getContent());
        state["answer"] = response.content

    # 等价 Java：logger.info("[WriterAgent] done in {}ms", System.currentTimeMillis()-t0);
    logger.info(f"[WriterAgent] done in {int((time.time()-t0)*1000)}ms")
    # 等价 Java：return state;
    return state