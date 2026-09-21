"""
MCP Resources — 只读数据源
- scrum://projects
- scrum://sprint/{sprintId}/board
- scrum://sprint/{sprintId}/burndown
- scrum://user/me/tasks
"""

import json
from typing import Callable

from mcp.types import Resource

from client.api import ApiClient


def register_resources(api: ApiClient) -> list[tuple[Resource, Callable]]:
    return [
        _projects_resource(api),
        _sprint_board_resource(api),
        _sprint_burndown_resource(api),
        _my_tasks_resource(api),
    ]


def _projects_resource(api: ApiClient):
    res = Resource(
        uri="scrum://projects",
        name="所有 Scrum 项目",
        description="系统中所有项目的列表",
        mimeType="text/plain",
    )

    async def handler(params: dict) -> str:
        projects = await api.get("/projects")
        if not projects:
            return "暂无项目"
        lines = []
        for p in projects:
            lines.append(
                f"[ID:{p['id']}] {p['name']}\n"
                f"  描述: {p.get('description') or '无'}\n"
                f"  状态: {p.get('status', '-')}"
            )
        return "\n\n".join(lines)

    return res, handler


def _sprint_board_resource(api: ApiClient):
    res = Resource(
        uri="scrum://sprint/{sprintId}/board",
        name="Sprint 看板",
        description="按状态分组展示 Sprint 任务看板",
        mimeType="text/plain",
    )

    async def handler(params: dict) -> str:
        sprint_id = params.get("sprintId", "")
        tasks = await api.get(f"/tasks/sprint/{sprint_id}")

        groups: dict[str, list] = {
            "TODO": [], "IN_PROGRESS": [], "IN_REVIEW": [], "DONE": [], "BLOCKED": []
        }
        for t in tasks:
            st = t.get("status", "TODO")
            groups.setdefault(st, []).append(t)

        lines = [f"Sprint {sprint_id} 看板\n{'='*40}"]
        for status, items in groups.items():
            if items:
                lines.append(f"\n[{status}] ({len(items)} 个任务)")
                for t in items:
                    assignee = (t.get("assignee") or {}).get("username", "未分配") if isinstance(t.get("assignee"), dict) else "未分配"
                    lines.append(f"  • [{t.get('taskKey','-')}] {t['title']} ({assignee})")
        return "\n".join(lines)

    return res, handler


def _sprint_burndown_resource(api: ApiClient):
    res = Resource(
        uri="scrum://sprint/{sprintId}/burndown",
        name="Sprint 燃尽图",
        description="Sprint 燃尽图数据",
        mimeType="text/plain",
    )

    async def handler(params: dict) -> str:
        sprint_id = params.get("sprintId", "")
        points = await api.get(f"/burndown/sprints/{sprint_id}")
        if not points:
            return f"Sprint {sprint_id} 暂无燃尽图数据"
        lines = [f"Sprint {sprint_id} 燃尽图数据"]
        for p in points:
            lines.append(
                f"  {p.get('pointDate','-')}: 实际={p.get('actualRemaining','-')} 理想={p.get('idealRemaining','-')}"
            )
        return "\n".join(lines)

    return res, handler


def _my_tasks_resource(api: ApiClient):
    res = Resource(
        uri="scrum://user/me/tasks",
        name="我的任务",
        description="当前登录用户分配的所有任务",
        mimeType="text/plain",
    )

    async def handler(params: dict) -> str:
        # 通过工作日志间接获取当前用户信息
        # 后端无 /tasks/me 接口，返回提示
        logs = await api.get("/worklogs/my-worklogs")
        task_ids = list({log.get("taskId") for log in logs if log.get("taskId")})
        if not task_ids:
            return "当前用户暂无任务记录"
        return f"当前用户有工作日志涉及的任务 ID: {task_ids[:10]}\n提示: 使用 get_active_sprint_tasks 查看完整任务列表"

    return res, handler
