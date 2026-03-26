"""
搜索相关工具
- search_similar_tasks → POST /similarity/search
"""

from typing import Callable

from mcp.types import Tool

from client.api import ApiClient


def register_search_tools(api: ApiClient) -> list[tuple[Tool, Callable]]:
    return [_search_similar_tasks(api)]


def _search_similar_tasks(api: ApiClient):
    tool = Tool(
        name="search_similar_tasks",
        description="基于关键词语义搜索相似任务（RAG 检索）",
        inputSchema={
            "type": "object",
            "properties": {
                "query":      {"type": "string",  "description": "搜索关键词或描述"},
                "project_id": {"type": "integer", "description": "限定项目范围（可选）"},
                "top_k":      {"type": "integer", "description": "返回数量，默认 5", "default": 5},
            },
            "required": ["query"],
        },
    )

    async def handler(args: dict) -> str:
        body = {
            "query":     args["query"],
            "projectId": args.get("project_id"),
            "topK":      args.get("top_k", 5),
        }
        resp = await api.post("/similarity/search", body)

        if not resp.get("success"):
            return f"搜索失败: {resp.get('error', '未知错误')}"

        tasks = resp.get("tasks", [])
        if not tasks:
            return f"未找到与 '{args['query']}' 相似的任务"

        lines = [f"搜索 '{args['query']}' 相似任务（共 {resp.get('count', len(tasks))} 条，耗时 {resp.get('durationMs', '-')}ms）:"]
        for t in tasks:
            lines.append(
                f"\n  [{t.get('taskKey', '-')}] {t.get('title', '-')}\n"
                f"    相似度: {t.get('similarityScore', 0):.3f}  "
                f"状态: {t.get('status', '-')}  "
                f"优先级: {t.get('priority', '-')}"
            )
        return "\n".join(lines)

    return tool, handler
