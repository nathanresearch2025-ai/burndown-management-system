"""
任务相关工具
- create_task                  → POST /tasks
- update_task_status           → PATCH /tasks/{id}/status?status=XXX
- ai_generate_task_description → POST /tasks/ai/generate-description
- get_my_worklogs              → GET /worklogs/my-worklogs
"""

from typing import Callable

from mcp.types import Tool

from client.api import ApiClient


def register_task_tools(api: ApiClient) -> list[tuple[Tool, Callable]]:
    return [
        _create_task(api),
        _update_task_status(api),
        _ai_generate_task_description(api),
        _get_my_worklogs(api),
    ]


def _create_task(api: ApiClient):
    tool = Tool(
        name="create_task",
        description="在指定 Sprint 中创建新任务",
        inputSchema={
            "type": "object",
            "properties": {
                "project_id":   {"type": "integer", "description": "项目 ID"},
                "sprint_id":    {"type": "integer", "description": "Sprint ID"},
                "title":        {"type": "string",  "description": "任务标题"},
                "description":  {"type": "string",  "description": "任务描述"},
                "type":         {
                    "type": "string",
                    "description": "任务类型",
                    "enum": ["FEATURE", "BUG", "TASK", "TECH_DEBT", "STORY"],
                },
                "priority":     {
                    "type": "string",
                    "description": "优先级",
                    "enum": ["LOW", "MEDIUM", "HIGH", "CRITICAL"],
                },
                "story_points": {"type": "number", "description": "故事点数"},
                "assignee_id":  {"type": "integer", "description": "负责人用户 ID（可选）"},
            },
            "required": ["project_id", "sprint_id", "title", "type", "priority"],
        },
    )

    async def handler(args: dict) -> str:
        body = {
            "projectId":   args["project_id"],
            "sprintId":    args["sprint_id"],
            "title":       args["title"],
            "description": args.get("description", ""),
            "type":        args["type"],
            "priority":    args["priority"],
            "storyPoints": args.get("story_points"),
            "assigneeId":  args.get("assignee_id"),
        }
        task = await api.post("/tasks", body)
        return (
            f"任务已创建\n"
            f"  Key: {task.get('taskKey', '-')}\n"
            f"  ID:  {task.get('id', '-')}\n"
            f"  标题: {task.get('title', '-')}\n"
            f"  状态: {task.get('status', '-')}"
        )

    return tool, handler


def _update_task_status(api: ApiClient):
    tool = Tool(
        name="update_task_status",
        description="更新任务状态（任务流转：TODO → IN_PROGRESS → IN_REVIEW → DONE）",
        inputSchema={
            "type": "object",
            "properties": {
                "task_id": {"type": "integer", "description": "任务 ID"},
                "status":  {
                    "type": "string",
                    "description": "新状态",
                    "enum": ["TODO", "IN_PROGRESS", "IN_REVIEW", "DONE", "BLOCKED"],
                },
            },
            "required": ["task_id", "status"],
        },
    )

    async def handler(args: dict) -> str:
        task_id = args["task_id"]
        status = args["status"]
        task = await api.patch(f"/tasks/{task_id}/status", params={"status": status})
        return (
            f"任务状态已更新\n"
            f"  Key: {task.get('taskKey', '-')}\n"
            f"  标题: {task.get('title', '-')}\n"
            f"  新状态: {task.get('status', '-')}"
        )

    return tool, handler


def _ai_generate_task_description(api: ApiClient):
    tool = Tool(
        name="ai_generate_task_description",
        description="使用 AI（RAG）根据任务标题和类型自动生成任务描述",
        inputSchema={
            "type": "object",
            "properties": {
                "project_id":   {"type": "integer", "description": "项目 ID"},
                "title":        {"type": "string",  "description": "任务标题"},
                "type":         {
                    "type": "string",
                    "description": "任务类型",
                    "enum": ["FEATURE", "BUG", "TASK", "TECH_DEBT", "STORY"],
                },
                "priority":     {
                    "type": "string",
                    "description": "优先级",
                    "enum": ["LOW", "MEDIUM", "HIGH", "CRITICAL"],
                },
                "story_points": {"type": "number", "description": "故事点数（可选）"},
            },
            "required": ["project_id", "title", "type", "priority"],
        },
    )

    async def handler(args: dict) -> str:
        body = {
            "projectId":   args["project_id"],
            "title":       args["title"],
            "type":        args["type"],
            "priority":    args["priority"],
            "storyPoints": args.get("story_points"),
        }
        resp = await api.post("/tasks/ai/generate-description", body)
        desc = resp.get("description", "（无描述）")
        refs = resp.get("similarTasks", [])
        result = f"AI 生成描述:\n{desc}"
        if refs:
            result += f"\n\n参考相似任务 ({len(refs)} 个):"
            for r in refs[:3]:
                result += f"\n  - [{r.get('taskKey', '-')}] {r.get('title', '-')} (相似度: {r.get('similarityScore', 0):.2f})"
        return result

    return tool, handler


def _get_my_worklogs(api: ApiClient):
    tool = Tool(
        name="get_my_worklogs",
        description="获取当前登录用户的工作日志记录",
        inputSchema={
            "type": "object",
            "properties": {},
            "required": [],
        },
    )

    async def handler(args: dict) -> str:
        logs = await api.get("/worklogs/my-worklogs")
        if not logs:
            return "暂无工作日志"
        lines = [f"工作日志（共 {len(logs)} 条）:"]
        for log in logs[:20]:  # 最多显示20条
            lines.append(
                f"  [{log.get('logDate', '-')}] "
                f"任务: {log.get('taskId', '-')}  "
                f"用时: {log.get('hoursSpent', '-')}h  "
                f"备注: {log.get('description') or '无'}"
            )
        return "\n".join(lines)

    return tool, handler
