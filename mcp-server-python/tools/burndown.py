"""
燃尽图相关工具
- get_burndown_data               → GET /burndown/sprints/{sprintId}
- get_sprint_completion_probability → GET /sprints/{id}/completion-probability
- trigger_burndown_snapshot       → POST /burndown/sprints/{sprintId}/calculate
"""

from typing import Callable

from mcp.types import Tool

from client.api import ApiClient


def register_burndown_tools(api: ApiClient) -> list[tuple[Tool, Callable]]:
    return [
        _get_burndown_data(api),
        _get_sprint_completion_probability(api),
        _trigger_burndown_snapshot(api),
    ]


def _get_burndown_data(api: ApiClient):
    tool = Tool(
        name="get_burndown_data",
        description="获取指定 Sprint 的燃尽图数据点，返回每日理想线与实际剩余工作量对比",
        inputSchema={
            "type": "object",
            "properties": {
                "sprint_id": {"type": "integer", "description": "Sprint ID"},
            },
            "required": ["sprint_id"],
        },
    )

    async def handler(args: dict) -> str:
        sprint_id = args["sprint_id"]
        points = await api.get(f"/burndown/sprints/{sprint_id}")
        if not points:
            return f"Sprint {sprint_id} 暂无燃尽图数据，可使用 trigger_burndown_snapshot 生成"

        header = f"Sprint {sprint_id} 燃尽图（共 {len(points)} 天数据）\n"
        header += f"{'日期':<12} {'理想剩余':>8} {'实际剩余':>8} {'完成任务':>8} {'总任务':>6}\n"
        header += "-" * 50

        rows = []
        for p in points:
            rows.append(
                f"{str(p.get('pointDate', '-')):<12}"
                f"{str(p.get('idealRemaining', '-')):>8}"
                f"{str(p.get('actualRemaining', '-')):>8}"
                f"{str(p.get('completedTasks', '-')):>8}"
                f"{str(p.get('totalTasks', '-')):>6}"
            )

        latest = points[-1]
        trend = "数据不足"
        if len(points) >= 2:
            prev = points[-2].get("actualRemaining", 0) or 0
            curr = latest.get("actualRemaining", 0) or 0
            trend = "↓ 下降（正常）" if curr <= prev else "↑ 上升（警告）"

        summary = (
            f"\n最新: {latest.get('pointDate')} | "
            f"实际剩余: {latest.get('actualRemaining')} | "
            f"理想剩余: {latest.get('idealRemaining')} | "
            f"趋势: {trend}"
        )

        return header + "\n" + "\n".join(rows) + summary

    return tool, handler


def _get_sprint_completion_probability(api: ApiClient):
    tool = Tool(
        name="get_sprint_completion_probability",
        description="使用机器学习模型预测指定 Sprint 的完成概率和风险等级（GREEN/YELLOW/RED）",
        inputSchema={
            "type": "object",
            "properties": {
                "sprint_id": {"type": "integer", "description": "Sprint ID"},
            },
            "required": ["sprint_id"],
        },
    )

    async def handler(args: dict) -> str:
        sprint_id = args["sprint_id"]
        try:
            data = await api.get(f"/sprints/{sprint_id}/completion-probability")
        except RuntimeError as e:
            return f"无法获取预测数据: {e}"

        prob = data.get("probability", 0)
        risk = data.get("riskLevel", "UNKNOWN")
        risk_label = {"GREEN": "🟢 低风险", "YELLOW": "🟡 中风险", "RED": "🔴 高风险"}.get(risk, risk)

        lines = [
            f"Sprint {sprint_id} 完成概率预测",
            f"  完成概率: {prob * 100:.1f}%",
            f"  风险等级: {risk_label}",
        ]

        fs = data.get("featureSummary")
        if fs:
            lines.append("\n关键特征:")
            if fs.get("daysElapsedRatio") is not None:
                lines.append(f"  时间消耗比: {fs['daysElapsedRatio'] * 100:.1f}%")
            if fs.get("remainingRatio") is not None:
                lines.append(f"  剩余工作比: {fs['remainingRatio'] * 100:.1f}%")
            if fs.get("velocityCurrent") is not None:
                lines.append(f"  当前速度: {fs['velocityCurrent']:.2f} SP/天")
            if fs.get("velocityAvg") is not None:
                lines.append(f"  历史均速: {fs['velocityAvg']:.2f} SP/天")
            if fs.get("blockedStories") is not None:
                lines.append(f"  阻断任务: {fs['blockedStories']} 个")

        return "\n".join(lines)

    return tool, handler


def _trigger_burndown_snapshot(api: ApiClient):
    tool = Tool(
        name="trigger_burndown_snapshot",
        description="手动触发指定 Sprint 的燃尽图快照计算（当天数据点）",
        inputSchema={
            "type": "object",
            "properties": {
                "sprint_id": {"type": "integer", "description": "Sprint ID"},
            },
            "required": ["sprint_id"],
        },
    )

    async def handler(args: dict) -> str:
        sprint_id = args["sprint_id"]
        await api.post(f"/burndown/sprints/{sprint_id}/calculate")
        return f"Sprint {sprint_id} 燃尽图快照已触发，请稍后调用 get_burndown_data 查看结果"

    return tool, handler
