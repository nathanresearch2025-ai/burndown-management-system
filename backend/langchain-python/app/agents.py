from langchain.agents import AgentExecutor, create_react_agent  # LangChain Agent 执行器
from langchain_core.prompts import PromptTemplate  # 提示词模板
from langchain_core.tools import Tool  # 工具包装
from langchain_core.messages import HumanMessage, SystemMessage  # 消息类型
from typing import List  # 类型注解

from .llm import build_llm  # 构建 LLM 实例
from . import tools as backend_tools  # 引入后端工具方法


SYSTEM_PLANNER = """你是 Planner Agent。只负责将用户问题拆解为 3-6 个可执行步骤，禁止调用工具。返回 JSON 数组。"""  # Planner 提示词
SYSTEM_ANALYST = """你是 Analyst Agent。只负责基于数据分析趋势与风险，禁止调用工具。"""  # Analyst 提示词
SYSTEM_WRITER = """你是 Writer Agent。只负责生成最终摘要。"""  # Writer 提示词

# ReAct 格式提示词，严格要求 Thought/Action/Action Input 格式
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


def build_tools(project_id: int, sprint_id: int, user_id: int) -> List[Tool]:  # 构建工具列表，参数已固定
    print(f"[DEBUG] build_tools called: project_id={project_id}, sprint_id={sprint_id}, user_id={user_id}")
    return [
        Tool.from_function(
            func=lambda _: (
                print(f"[DEBUG] getInProgressTasks -> project_id={project_id}, user_id={user_id}") or
                backend_tools.get_in_progress_tasks(project_id, user_id)
            ),
            name="getInProgressTasks",
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


def invoke_llm(system_prompt: str, user_input: str) -> str:  # 直接调用 LLM（无工具）
    print(f"[DEBUG] invoke_llm: system={system_prompt[:30]}... input={user_input[:80]}...")
    llm = build_llm()
    messages = [
        SystemMessage(content=system_prompt),
        HumanMessage(content=user_input),
    ]
    response = llm.invoke(messages)
    print(f"[DEBUG] invoke_llm response: {response.content[:100]}...")
    return response.content


def build_data_agent(project_id: int, sprint_id: int, user_id: int) -> AgentExecutor:  # 构建带工具的 Data Agent
    print(f"[DEBUG] build_data_agent: project_id={project_id}, sprint_id={sprint_id}, user_id={user_id}")
    llm = build_llm()
    tools = build_tools(project_id, sprint_id, user_id)  # 工具已绑定具体参数
    react_prompt = PromptTemplate.from_template(REACT_PROMPT_TEMPLATE)
    agent = create_react_agent(llm, tools, react_prompt)
    return AgentExecutor(
        agent=agent,
        tools=tools,
        verbose=True,
        handle_parsing_errors=True,
        max_iterations=6,  # 防止无限循环
    )


def run_multi_agent(question: str, project_id: int, sprint_id: int, user_id: int):  # 多 Agent 流水线编排
    print(f"\n{'='*60}")
    print(f"[DEBUG] run_multi_agent START")
    print(f"[DEBUG] question={question}")
    print(f"[DEBUG] project_id={project_id}, sprint_id={sprint_id}, user_id={user_id}")
    print(f"{'='*60}\n")

    print("[DEBUG] Step 1: Planner invoking...")
    plan = invoke_llm(SYSTEM_PLANNER, question)
    print(f"[DEBUG] Planner result: {plan}\n")

    print("[DEBUG] Step 2: Data Agent invoking...")
    data_agent = build_data_agent(project_id, sprint_id, user_id)
    data_input = (
        f"请依次调用所有工具获取数据。\n"
        f"计划：{plan}\n"
        f"参数已内置，每个工具传入任意字符串即可（如 'run'）。"
    )
    print(f"[DEBUG] Data Agent input: {data_input}")
    data_result = data_agent.invoke({"input": data_input})
    data = data_result.get("output", str(data_result))
    print(f"[DEBUG] Data Agent result: {data}\n")

    print("[DEBUG] Step 3: Analyst invoking...")
    analysis = invoke_llm(SYSTEM_ANALYST, f"数据如下: {data}")
    print(f"[DEBUG] Analyst result: {analysis}\n")

    print("[DEBUG] Step 4: Writer invoking...")
    summary = invoke_llm(SYSTEM_WRITER, f"分析结论: {analysis}")
    print(f"[DEBUG] Writer result: {summary}\n")

    print("[DEBUG] run_multi_agent DONE")
    return {
        "plan": plan,
        "data": data,
        "analysis": analysis,
        "summary": summary,
    }
