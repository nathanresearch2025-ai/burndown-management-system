"""
Sprint 相关工具
- get_projects        → GET /projects
- get_active_sprint   → GET /sprints/project/{projectId} 过滤 ACTIVE
- get_active_sprint_tasks → GET /tasks/sprint/{sprintId}
"""

import json
from typing import Any, Callable, Optional

from mcp.types import Tool

from client.api import ApiClient


def register_sprint_tools(api: ApiClient) -> list[tuple[Tool, Callable]]:
    return [
        _get_projects(api),
        _get_active_sprint(api),
        _get_active_sprint_tasks(api),
    ]


def _get_projects(api: ApiClient):
    tool = Tool(
        name="get_projects",
        description="获取系统中所有 Scrum 项目列表，返回项目 ID、名称、描述、负责人",
        inputSchema={
            "type": "object",
            "properties": {},
            "required": [],
        },
    )

    async def handler(args: dict) -> str:
        projects = await api.get("/projects")
        if not projects:
            return "暂无项目"
        lines = []
        for p in projects:
            lines.append(
                f"[ID:{p['id']}] {p['name']}\n"
                f"  描述: {p.get('description') or '无'}\n"
                f"  负责人: {p.get('owner', {}).get('username', '未知') if isinstance(p.get('owner'), dict) else '未知'}\n"
                f"  状态: {p.get('status', '-')}"
            )
        return "\n\n".join(lines)

    return tool, handler


def _get_active_sprint(api: ApiClient):
    tool = Tool(
        name="get_active_sprint",
        description="获取指定项目当前活跃（ACTIVE）的 Sprint 信息",
        inputSchema={
            "type": "object",
            "properties": {
                "project_id": {
                    "type": "integer",
                    "description": "项目 ID",
                }
            },
            "required": ["project_id"],
        },
    )

    async def handler(args: dict) -> str:
        project_id = args["project_id"]
        sprints = await api.get(f"/sprints/project/{project_id}")
        active = [s for s in sprints if s.get("status") == "ACTIVE"]
        if not active:
            return f"项目 {project_id} 当前没有活跃的 Sprint"
        s = active[0]
        return (
            f"活跃 Sprint: {s['name']}\n"
            f"  ID: {s['id']}\n"
            f"  目标: {s.get('goal') or '未设置'}\n"
            f"  开始: {s.get('startDate', '-')}  结束: {s.get('endDate', '-')}\n"
            f"  承诺故事点: {s.get('committedPoints', '-')}  已完成: {s.get('completedPoints', '-')}"
        )

    return tool, handler


def _get_active_sprint_tasks(api: ApiClient):
    tool = Tool(
        name="get_active_sprint_tasks",
        description="获取指定 Sprint 的任务列表，可按状态过滤（TODO/IN_PROGRESS/IN_REVIEW/DONE/BLOCKED）",
        inputSchema={
            "type": "object",
            "properties": {
                "sprint_id": {
                    "type": "integer",
                    "description": "Sprint ID",
                },
                "status": {
                    "type": "string",
                    "description": "按状态过滤，不填返回全部",
                    "enum": ["TODO", "IN_PROGRESS", "IN_REVIEW", "DONE", "BLOCKED"],
                },
            },
            "required": ["sprint_id"],
        },
    )

    async def handler(args: dict) -> str:
        sprint_id = args["sprint_id"]
        status_filter: Optional[str] = args.get("status")

        tasks = await api.get(f"/tasks/sprint/{sprint_id}")
        if status_filter:
            tasks = [t for t in tasks if t.get("status") == status_filter]

        if not tasks:
            label = f"状态={status_filter}" if status_filter else "全部"
            return f"Sprint {sprint_id} 没有符合条件的任务（{label}）"

        lines = [f"Sprint {sprint_id} 任务列表（共 {len(tasks)} 条）：\n"]
        for t in tasks:
            assignee = (t.get("assignee") or {}).get("username", "未分配") if isinstance(t.get("assignee"), dict) else "未分配"
            lines.append(
                f"  [{t.get('taskKey', '-')}] {t['title']}\n"
                f"    状态: {t.get('status', '-')}  优先级: {t.get('priority', '-')}  "
                f"故事点: {t.get('storyPoints', '-')}  负责人: {assignee}"
            )
        return "\n".join(lines)

    return tool, handler
