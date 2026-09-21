# AgentExecutor：驱动 Agent 循环执行 Thought→Action→Observation，直到输出 Final Answer
# create_react_agent：将 LLM + 工具列表 + ReAct 提示词组合成一个 Agent 推理链对象
from langchain.agents import AgentExecutor, create_react_agent
# PromptTemplate：提示词模板，用 {变量名} 占位符，调用时动态填充
from langchain_core.prompts import PromptTemplate
# Tool：LangChain 工具包装类，将普通 Python 函数包装成 Agent 可识别的工具格式
from langchain_core.tools import Tool
# HumanMessage：代表用户输入的消息；SystemMessage：代表系统角色指令的消息
from langchain_core.messages import HumanMessage, SystemMessage
# 类型注解：List 列表、Optional 可空、Dict 字典、Any 任意类型
from typing import List, Optional, Dict, Any
import os       # 读取环境变量
import time     # 计时，用于性能日志
import asyncio  # 异步并发，asyncio.gather 并行调用多个工具
import json     # 解析 LLM 返回的 JSON 字符串
import anyio    # 跨平台异步库，to_thread.run_sync 将同步函数放入线程池执行

# build_llm：构建并返回全局复用的 LLM 客户端实例（lru_cache 保证单例）
from .llm import build_llm
# backend_tools：tools.py 模块，包含三个异步工具函数，负责调用 Spring Boot 后端接口
from . import tools as backend_tools


# ── 各 Agent 的系统提示词 ──────────────────────────────────────────────────────

# Planner Agent 提示词：只负责拆解问题为步骤，禁止调用工具，输出 JSON 数组
SYSTEM_PLANNER = """你是 Planner Agent。只负责将用户问题拆解为 3-6 个可执行步骤，禁止调用工具。返回 JSON 数组。"""

# Analyst Agent 提示词：只负责基于已有数据做趋势与风险分析，不调用工具
SYSTEM_ANALYST = """你是 Analyst Agent。只负责基于数据分析趋势与风险，禁止调用工具。"""

# Writer Agent 提示词：只负责将分析结论整理为最终摘要
SYSTEM_WRITER = """你是 Writer Agent。只负责生成最终摘要。"""

# Summarizer Agent 提示词（fast pipeline 专用）：
# 接收用户问题 + 三个工具的原始输出，严格输出一个可解析的 JSON 对象
SYSTEM_SUMMARIZER = """你是 Scrum 站会助手。你会收到：用户问题 + 三个工具的原始输出（字符串）。
请严格输出一个 JSON 对象，字段如下：
{
  "answer": "中文最终回答（结论 + 证据 + 建议）",
  "toolsUsed": ["getInProgressTasks","getSprintBurndown","evaluateBurndownRisk"],
  "evidence": ["从工具输出中摘取的关键证据行/关键数值，最多 6 条"],
  "riskLevel": "LOW|MEDIUM|HIGH|UNKNOWN"
}
约束：
- 不要编造工具输出中不存在的数据；
- 如果 sprintId 为 0 或燃尽数据缺失，riskLevel 输出 UNKNOWN，并在 answer 里说明原因；
- JSON 必须可解析，不要额外输出解释文本。
"""

# ReAct 格式提示词（legacy pipeline 的 Data Agent 专用）
# 严格要求 LLM 按 Thought/Action/Action Input/Observation/Final Answer 格式输出
# {tools}：由 LangChain 自动填充工具描述列表
# {tool_names}：由 LangChain 自动填充工具名称列表
# {input}：用户输入
# {agent_scratchpad}：Agent 已执行的中间步骤记录（LangChain 自动维护）
REACT_PROMPT_TEMPLATE = """你是 Data Agent，只负责调用工具获取数据，不做分析。

你必须严格按照以下格式逐步输出，禁止用对话方式回复：

Thought: 我需要调用哪个工具
Action: 工具名称（必须是工具列表中的一个）
Action Input: 工具的输入参数（字符串格式）
Observation: 工具返回结果
... (可重复 Thought/Action/Action Input/Observation)
Thought: 我已获取所有数据
Final Answer: 汇总所有获取到的数据

可用工具：
{tools}

工具名称列表：{tool_names}

开始执行：
{input}

{agent_scratchpad}"""

def build_tools(project_id: int, sprint_id: int, user_id: int) -> List[Tool]:
    """构建工具列表，将三个后端工具函数包装为 LangChain Tool 对象。
    参数在此处通过闭包绑定，Agent 调用工具时无需再传参数。
    """
    print(f"[DEBUG] build_tools called: project_id={project_id}, sprint_id={sprint_id}, user_id={user_id}")
    return [
        Tool.from_function(
            # lambda _ 忽略 Agent 传入的参数（工具已通过闭包绑定了具体值）
            # or 写法：print 返回 None，None or X 取右值，即实际工具调用结果
            func=lambda _: (
                print(f"[DEBUG] getInProgressTasks -> project_id={project_id}, user_id={user_id}") or
                backend_tools.get_in_progress_tasks(project_id, user_id)  # 调用同步版本（legacy pipeline 使用）
            ),
            name="getInProgressTasks",  # Agent 在 Action 行中必须精确使用此名称
            description=f"获取项目 {project_id} 中用户 {user_id} 的进行中任务列表，无需额外参数直接调用",
        ),
        Tool.from_function(
            func=lambda _: (
                print(f"[DEBUG] getSprintBurndown -> project_id={project_id}, sprint_id={sprint_id}") or
                backend_tools.get_sprint_burndown(project_id, sprint_id)
            ),
            name="getSprintBurndown",
            description=f"获取项目 {project_id} Sprint {sprint_id} 的燃尽图数据，无需额外参数直接调用",
        ),
        Tool.from_function(
            func=lambda _: (
                print(f"[DEBUG] evaluateBurndownRisk -> project_id={project_id}, sprint_id={sprint_id}") or
                backend_tools.evaluate_burndown_risk(project_id, sprint_id)
            ),
            name="evaluateBurndownRisk",
            description=f"评估项目 {project_id} Sprint {sprint_id} 的燃尽风险，无需额外参数直接调用",
        ),
    ]


def invoke_llm(system_prompt: str, user_input: str) -> str:
    """同步调用 LLM（无工具），用于 legacy pipeline 中的 Planner/Analyst/Writer Agent。"""
    print(f"[DEBUG] invoke_llm: system={system_prompt[:30]}... input={user_input[:80]}...")
    llm = build_llm()  # 获取单例 LLM 实例
    messages = [
        SystemMessage(content=system_prompt),  # 系统角色指令，定义 Agent 身份和行为约束
        HumanMessage(content=user_input),      # 用户输入内容
    ]
    # invoke：同步调用 LLM，返回 AIMessage 对象，.content 取文本内容
    response = llm.invoke(messages)
    print(f"[DEBUG] invoke_llm response: {response.content[:100]}...")
    return response.content


def build_data_agent(project_id: int, sprint_id: int, user_id: int) -> AgentExecutor:
    """构建带工具的 Data Agent（legacy pipeline 专用）。
    使用 ReAct 框架：LLM 通过 Thought/Action/Observation 循环逐步调用工具收集数据。
    """
    print(f"[DEBUG] build_data_agent: project_id={project_id}, sprint_id={sprint_id}, user_id={user_id}")
    llm = build_llm()  # 获取单例 LLM 实例
    tools = build_tools(project_id, sprint_id, user_id)  # 构建已绑定参数的工具列表

    # 从模板字符串创建 PromptTemplate，变量名必须与模板中的 {变量名} 对应
    react_prompt = PromptTemplate.from_template(REACT_PROMPT_TEMPLATE)

    # create_react_agent：将 LLM + 工具 + 提示词组合为 Runnable Agent 对象
    agent = create_react_agent(llm, tools, react_prompt)

    return AgentExecutor(
        agent=agent,             # 上面创建的 Agent 推理链
        tools=tools,             # 工具列表（AgentExecutor 用它执行 Action）
        verbose=False,           # 不打印 LangChain 内部的每步推理日志
        handle_parsing_errors=True,  # LLM 输出格式不符合 ReAct 要求时自动重试而非直接报错
        max_iterations=3,        # 最多循环 3 轮（防止 LLM 陷入无限工具调用循环）
    )


async def invoke_llm_async(system_prompt: str, user_input: str) -> str:
    """异步调用 LLM（fast pipeline 专用）。
    LangChain 的 llm.invoke 是同步阻塞调用，直接在协程中调用会阻塞事件循环。
    anyio.to_thread.run_sync 将其放入线程池执行，使事件循环保持响应。
    """
    llm = build_llm()
    messages = [
        SystemMessage(content=system_prompt),
        HumanMessage(content=user_input),
    ]
    # to_thread.run_sync：在独立线程中运行同步函数，await 等待结果，不阻塞事件循环
    response = await anyio.to_thread.run_sync(llm.invoke, messages)
    return response.content


async def run_fast_pipeline(
        question: str,
        project_id: int,
        sprint_id: int,
        user_id: int,
        trace_id: Optional[str] = None,
        tools_concurrent: bool = True,   # 是否并行调用三个工具（默认并行，节省时间）
        log_step_timing: bool = True,    # 是否打印各步骤耗时日志
) -> Dict[str, Any]:
    """Fast pipeline：直接并行调用三个工具，再由 Summarizer LLM 一次性汇总。
    相比 legacy pipeline（4 个 LLM 串行调用），只需 1 次 LLM 调用，延迟更低。
    """
    # 记录整体开始时间，用于计算 total_ms
    start = time.time()

    async def _tool_calls() -> Dict[str, str]:
        """内部函数：并行或串行调用三个后端工具，返回原始输出字典。"""
        # 构建进行中任务查询协程
        in_progress_coro = backend_tools.get_in_progress_tasks(project_id, user_id, trace_id=trace_id)

        if sprint_id and sprint_id > 0:
            # sprint_id 有效：正常调用燃尽图和风险评估接口
            burndown_coro = backend_tools.get_sprint_burndown(project_id, sprint_id, trace_id=trace_id)
            risk_coro = backend_tools.evaluate_burndown_risk(project_id, sprint_id, trace_id=trace_id)
        else:
            # sprint_id 为 0 或未提供：用占位协程跳过，避免无效 HTTP 请求
            async def _skip(msg: str) -> str:
                return msg
            burndown_coro = _skip("未提供 sprintId，跳过燃尽图查询")
            risk_coro = _skip("未提供 sprintId，跳过风险评估")

        t0 = time.time()
        if tools_concurrent:
            # asyncio.gather：并发等待所有协程，总耗时 ≈ 最慢那个工具的耗时
            in_progress, burndown, risk = await asyncio.gather(in_progress_coro, burndown_coro, risk_coro)
        else:
            # 串行调用：依次等待，总耗时 = 三个工具耗时之和（用于调试或限流场景）
            in_progress = await in_progress_coro
            burndown = await burndown_coro
            risk = await risk_coro

        # 计算三个工具调用的总耗时（毫秒）
        tools_ms = int((time.time() - t0) * 1000)

        if log_step_timing:
            print(f"[TIMING] traceId={trace_id} tools_ms_total={tools_ms}")

        # 返回以工具名为 key 的原始输出字典，供后续 LLM 汇总使用
        return {
            "getInProgressTasks": in_progress,
            "getSprintBurndown": burndown,
            "evaluateBurndownRisk": risk,
        }

    # 执行工具调用（并行或串行），得到三个工具的原始输出
    tools_out = await _tool_calls()

    # 记录 LLM 汇总开始时间
    t1 = time.time()

    # 将 traceId、用户问题和三个工具的原始输出拼接为 Summarizer 的输入
    # 格式化为带标签的文本块，帮助 LLM 区分不同工具的输出内容
    summarize_input = (
        f"traceId={trace_id}\n"
        f"用户问题：{question}\n\n"
        f"工具输出：\n"
        f"[getInProgressTasks]\n{tools_out['getInProgressTasks']}\n\n"
        f"[getSprintBurndown]\n{tools_out['getSprintBurndown']}\n\n"
        f"[evaluateBurndownRisk]\n{tools_out['evaluateBurndownRisk']}\n"
    )

    # 调用 Summarizer LLM：将工具输出汇总为结构化 JSON（answer/toolsUsed/evidence/riskLevel）
    summary_json = await invoke_llm_async(SYSTEM_SUMMARIZER, summarize_input)

    # 计算 LLM 汇总耗时和全链路总耗时
    llm_ms = int((time.time() - t1) * 1000)
    total_ms = int((time.time() - start) * 1000)

    if log_step_timing:
        print(f"[TIMING] traceId={trace_id} llm_summarize_ms={llm_ms} total_ms={total_ms}")

    # 解析 LLM 返回的 JSON 字符串
    # LLM 偶发输出不规范（如带 markdown 代码块）时，json.loads 会抛异常，进入 fallback
    try:
        parsed = json.loads(summary_json)
        return {
            "summary": parsed.get("answer", ""),           # 中文最终回答
            "toolsUsed": parsed.get("toolsUsed", ["getInProgressTasks", "getSprintBurndown", "evaluateBurndownRisk"]),
            "evidence": parsed.get("evidence", []),        # 关键证据片段列表
            "riskLevel": parsed.get("riskLevel", None),    # 风险等级
            "raw": parsed,                                  # 保留完整解析结果供调试
        }
    except Exception:
        # fallback：JSON 解析失败时，将原始字符串作为 summary 返回，保证接口可用
        return {
            "summary": summary_json,
            "toolsUsed": ["getInProgressTasks", "getSprintBurndown", "evaluateBurndownRisk"],
            "evidence": [],
            "riskLevel": None,
            "raw": {"unparsed": True},  # 标记本次响应未成功解析，便于监控告警
        }


def run_multi_agent(question: str, project_id: int, sprint_id: int, user_id: int):
    """Legacy pipeline：四个 Agent 串行执行的多 Agent 编排流程。
    流程：Planner → Data Agent（ReAct）→ Analyst → Writer
    相比 fast pipeline 多 3 次 LLM 调用，延迟更高，但推理链更清晰，便于调试。
    通过环境变量 STANDUP_PIPELINE_MODE=legacy 启用。
    """
    print(f"\n{'='*60}")
    print(f"[DEBUG] run_multi_agent START")
    print(f"[DEBUG] question={question}")
    print(f"[DEBUG] project_id={project_id}, sprint_id={sprint_id}, user_id={user_id}")
    print(f"{'='*60}\n")

    # Step 1：Planner Agent - 将用户问题拆解为 3-6 个可执行步骤（JSON 数组）
    print("[DEBUG] Step 1: Planner invoking...")
    plan = invoke_llm(SYSTEM_PLANNER, question)
    print(f"[DEBUG] Planner result: {plan}\n")

    # Step 2：Data Agent - 基于计划，通过 ReAct 循环调用三个工具收集真实数据
    print("[DEBUG] Step 2: Data Agent invoking...")
    data_agent = build_data_agent(project_id, sprint_id, user_id)
    data_input = (
        f"请依次调用所有工具获取数据。\n"
        f"计划：{plan}\n"
        f"问题：{question}"
    )
    print(f"[DEBUG] Data Agent input: {data_input}")
    # invoke：同步驱动 Agent 循环，直到输出 Final Answer 或达到 max_iterations
    data_result = data_agent.invoke({"input": data_input})
    # 优先取 output 字段（AgentExecutor 的标准输出键），失败则将整个结果转字符串
    data = data_result.get("output", str(data_result))
    print(f"[DEBUG] Data Agent result: {data}\n")

    # Step 3：Analyst Agent - 基于 Data Agent 收集的数据，分析趋势与风险
    print("[DEBUG] Step 3: Analyst invoking...")
    analysis = invoke_llm(SYSTEM_ANALYST, f"数据如下: {data}")
    print(f"[DEBUG] Analyst result: {analysis}\n")

    # Step 4：Writer Agent - 将分析结论整理为最终可读摘要
    print("[DEBUG] Step 4: Writer invoking...")
    summary = invoke_llm(SYSTEM_WRITER, f"分析结论: {analysis}")
    print(f"[DEBUG] Writer result: {summary}\n")

    print("[DEBUG] run_multi_agent DONE")
    # 返回四个阶段的完整输出，main.py 取 summary 作为最终回答
    return {
        "plan": plan,
        "data": data,
        "analysis": analysis,
        "summary": summary,
    }


def pipeline_mode() -> str:
    """读取流水线模式配置。
    返回值："fast"（默认，单次 LLM 汇总）或 "legacy"（四 Agent 串行）。
    """
    # strip().lower()：去除首尾空白并转小写，避免大小写或空格导致判断失误
    return os.getenv("STANDUP_PIPELINE_MODE", "fast").strip().lower()


def tools_concurrent_enabled() -> bool:
    """读取工具并行调用开关。
    返回 True 时 asyncio.gather 并行调用三个工具；False 时串行调用（用于调试或限流）。
    支持多种真值字符串："1", "true", "yes", "y", "on"（不区分大小写）。
    """
    return os.getenv("TOOLS_CONCURRENT", "true").strip().lower() in ("1", "true", "yes", "y", "on")


def log_step_timing_enabled() -> bool:
    """读取步骤耗时日志开关。
    返回 True 时打印 [TIMING] 日志，记录工具调用和 LLM 汇总的耗时（毫秒）。
    """
    return os.getenv("LOG_STEP_TIMING", "true").strip().lower() in ("1", "true", "yes", "y", "on")

