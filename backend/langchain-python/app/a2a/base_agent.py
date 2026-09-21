import logging
from typing import Dict, Any, Optional
import time

logger = logging.getLogger(__name__)


class BaseAgent:
    """A2A 通信基础类，提供 Agent 间调用能力"""

    def __init__(self, agent_name: str):
        self.agent_name = agent_name
        self.agent_registry: Dict[str, Any] = {}

    def register_agent(self, name: str, agent_instance):
        """注册其他 Agent 实例，用于 A2A 调用"""
        self.agent_registry[name] = agent_instance
        logger.info(f"[{self.agent_name}] Registered agent: {name}")

    async def call_agent(
        self,
        target_agent: str,
        input_state: Dict[str, Any],
        reason: str = ""
    ) -> Dict[str, Any]:
        """A2A 调用接口

        Args:
            target_agent: 目标 Agent 名称
            input_state: 传递给目标 Agent 的 state
            reason: 调用原因（用于日志和追踪）

        Returns:
            目标 Agent 返回的 state
        """
        if target_agent not in self.agent_registry:
            raise ValueError(f"Agent '{target_agent}' not registered")

        # 检查调用深度，防止循环调用
        a2a_trace = input_state.get("a2a_trace", [])
        if len(a2a_trace) >= 2:
            raise Exception(f"A2A call depth limit exceeded (max 2), possible circular call")

        logger.info(f"[A2A] {self.agent_name} → {target_agent}: {reason}")
        t0 = time.time()

        # 调用目标 Agent
        target = self.agent_registry[target_agent]
        result = await target.execute(input_state)

        # 记录调用链
        trace_entry = {
            "caller": self.agent_name,
            "callee": target_agent,
            "reason": reason,
            "duration_ms": int((time.time() - t0) * 1000),
        }
        result.setdefault("a2a_trace", []).append(trace_entry)

        logger.info(f"[A2A] {self.agent_name} ← {target_agent}: completed in {trace_entry['duration_ms']}ms")
        return result

    async def execute(self, state: Dict[str, Any]) -> Dict[str, Any]:
        """子类必须实现的执行方法"""
        raise NotImplementedError("Subclass must implement execute()")
