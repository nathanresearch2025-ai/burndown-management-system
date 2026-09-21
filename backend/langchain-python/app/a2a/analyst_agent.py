import logging
from typing import Dict, Any
from .base_agent import BaseAgent
from ..llm import build_llm
from langchain_core.messages import SystemMessage, HumanMessage
import json

logger = logging.getLogger(__name__)


class AnalystAgentA2A(BaseAgent):
    """AnalystAgent with A2A support - LLM 驱动的智能数据补充"""

    def __init__(self):
        super().__init__("analyst")

    async def execute(self, state: Dict[str, Any]) -> Dict[str, Any]:
        """执行风险分析，LLM 判断是否需要补充数据"""
        logger.info("[AnalystAgentA2A] Starting risk analysis")

        data_summary = state.get("data_summary", "")

        # LLM 判断是否需要补充数据
        need_supplement = await self._llm_decide_supplement(data_summary, state.get("question", ""))

        if need_supplement["need_supplement"]:
            logger.warning(f"[AnalystAgentA2A] LLM suggests data supplement: {need_supplement['reason']}")

            # A2A 调用：请求 DataAgent 补充
            supplement_result = await self.call_agent(
                "data",
                {
                    "question": need_supplement["request"],
                    "project_id": state["project_id"],
                    "sprint_id": state.get("sprint_id", 0),
                    "user_id": state["user_id"],
                    "trace_id": state.get("trace_id"),
                    "a2a_trace": state.get("a2a_trace", []),
                },
                reason=need_supplement["reason"]
            )

            # 合并补充数据和 A2A trace
            data_summary += "\n\n[补充数据]\n" + supplement_result.get("data_summary", "")
            state["data_summary"] = data_summary
            state["a2a_trace"] = supplement_result.get("a2a_trace", [])
            logger.info("[AnalystAgentA2A] Data supplemented, continuing analysis")

        # 调用 LLM 分析
        llm = build_llm()
        messages = [
            SystemMessage(content="你是风险分析专家。输出 JSON: {\"risk_level\": \"LOW|MEDIUM|HIGH\", \"conclusion\": \"分析结论\"}"),
            HumanMessage(content=f"数据摘要：\n{data_summary}\n\n请分析风险等级")
        ]

        response = await llm.ainvoke(messages)
        content = response.content

        # 解析 JSON
        try:
            result = json.loads(content)
            state["risk_level"] = result.get("risk_level", "UNKNOWN")
            state["conclusion"] = result.get("conclusion", content)
        except:
            # fallback
            if "HIGH" in content:
                state["risk_level"] = "HIGH"
            elif "MEDIUM" in content:
                state["risk_level"] = "MEDIUM"
            else:
                state["risk_level"] = "LOW"
            state["conclusion"] = content

        state["analysis"] = content
        logger.info(f"[AnalystAgentA2A] Analysis completed, risk_level={state['risk_level']}")
        return state

    async def _llm_decide_supplement(self, data_summary: str, question: str) -> Dict[str, Any]:
        """LLM 判断是否需要补充数据

        Returns:
            {
                "need_supplement": bool,
                "reason": str,
                "request": str
            }
        """
        llm = build_llm()
        prompt = f"""你是风险分析专家。用户问题：{question}

当前数据摘要：
{data_summary}

判断当前数据是否足够回答用户问题。如果缺少关键数据，需要补充。

常见缺失场景：
1. 只有燃尽图，缺少任务列表（无法判断工作分布）
2. 只有任务列表，缺少燃尽趋势（无法判断整体进度）
3. 数据时间范围不足

输出 JSON：
{{
    "need_supplement": true/false,
    "reason": "需要补充的原因",
    "request": "向 DataAgent 请求的具体内容"
}}"""

        messages = [
            SystemMessage(content="你是数据分析专家，输出严格的 JSON"),
            HumanMessage(content=prompt)
        ]

        response = await llm.ainvoke(messages)

        try:
            result = json.loads(response.content)
            return result
        except:
            return {"need_supplement": False, "reason": "", "request": ""}
