import logging
from typing import Dict, Any, Optional
from .base_agent import BaseAgent
from ..tools import get_in_progress_tasks, get_sprint_burndown, evaluate_burndown_risk
from ..llm import build_llm
from langchain_core.messages import SystemMessage, HumanMessage
import json

logger = logging.getLogger(__name__)


class DataAgentA2A(BaseAgent):
    """DataAgent with A2A support - LLM 驱动的智能 A2A 决策"""

    def __init__(self):
        super().__init__("data_agent")

    async def execute(self, state: Dict[str, Any]) -> Dict[str, Any]:
        """执行数据收集，LLM 判断是否需要 A2A 验证"""
        logger.info("[DataAgentA2A] Starting data collection")

        project_id = state["project_id"]
        sprint_id = state.get("sprint_id", 0)
        user_id = state["user_id"]
        trace_id = state.get("trace_id")
        force_a2a = state.get("force_a2a", False)

        # 调用工具
        tasks_data = await get_in_progress_tasks(project_id, user_id, trace_id)

        if sprint_id and sprint_id > 0:
            burndown_data = await get_sprint_burndown(project_id, sprint_id, trace_id)
            risk_data = await evaluate_burndown_risk(project_id, sprint_id, trace_id)

            # LLM 判断是否需要 A2A 验证（或强制触发）
            if force_a2a:
                need_verification = {
                    "need_verify": True,
                    "reason": "强制 A2A 测试模式",
                    "question": "请验证燃尽图数据的准确性和合理性"
                }
            else:
                need_verification = await self._llm_decide_verification(burndown_data)

            if need_verification["need_verify"]:
                logger.warning(f"[DataAgentA2A] LLM suggests verification: {need_verification['reason']}")

                # A2A 调用：请求 AnalystAgent 验证
                verification_result = await self.call_agent(
                    "analyst",
                    {
                        "question": need_verification["question"],
                        "data_summary": burndown_data,
                        "project_id": project_id,
                        "sprint_id": sprint_id,
                        "user_id": user_id,
                        "trace_id": trace_id,
                        "a2a_trace": state.get("a2a_trace", []),
                    },
                    reason=need_verification["reason"]
                )

                # 合并 A2A trace
                state["a2a_trace"] = verification_result.get("a2a_trace", [])

                if "DATA_ERROR" in verification_result.get("conclusion", ""):
                    state["data_summary"] = f"任务数据：{tasks_data}\n\n燃尽数据异常（已标记为不可信）：{burndown_data}"
                    state["error"] = "burndown_data_anomaly"
                else:
                    state["data_summary"] = f"任务数据：{tasks_data}\n\n燃尽数据（已验证）：{burndown_data}\n\n风险评估：{risk_data}"
            else:
                state["data_summary"] = f"任务数据：{tasks_data}\n\n燃尽数据：{burndown_data}\n\n风险评估：{risk_data}"
        else:
            state["data_summary"] = f"任务数据：{tasks_data}\n\n未提供 sprintId，跳过燃尽图查询"

        state["tools_used"] = ["getInProgressTasks", "getSprintBurndown", "evaluateBurndownRisk"]
        logger.info("[DataAgentA2A] Data collection completed")
        return state

    async def _llm_decide_verification(self, burndown_data: str) -> Dict[str, Any]:
        """LLM 判断燃尽图数据是否需要验证

        Returns:
            {
                "need_verify": bool,
                "reason": str,
                "question": str
            }
        """
        llm = build_llm()
        prompt = f"""你是数据质量检查专家。分析以下燃尽图数据，判断是否存在异常需要进一步验证。

燃尽图数据：
{burndown_data}

常见异常模式：
1. 最近3天 actual_remaining 完全相同（进度停滞）
2. actual_remaining 突然暴增（可能数据错误）
3. ideal_remaining 为负数（数据异常）
4. 数据缺失或格式错误

请输出 JSON：
{{
    "need_verify": true/false,
    "reason": "异常原因（如果有）",
    "question": "需要验证的具体问题（如果 need_verify=true）"
}}"""

        messages = [
            SystemMessage(content="你是数据质量专家，输出严格的 JSON 格式"),
            HumanMessage(content=prompt)
        ]

        response = await llm.ainvoke(messages)

        try:
            result = json.loads(response.content)
            return result
        except:
            # fallback：解析失败时不验证
            return {"need_verify": False, "reason": "", "question": ""}
