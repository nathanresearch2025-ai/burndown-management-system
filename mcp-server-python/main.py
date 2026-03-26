"""
Burndown MCP Server - Python 实现
基于 mcp SDK，封装 Burndown Management System REST API
"""

import asyncio
import logging
import os
import sys
from typing import Optional

from dotenv import load_dotenv

load_dotenv()

# 配置日志 - 同时输出到文件和控制台
log_file = os.path.join(os.path.dirname(__file__), "mcp.log")
logging.basicConfig(
    level=getattr(logging, os.getenv("LOG_LEVEL", "INFO")),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    handlers=[
        logging.FileHandler(log_file, encoding="utf-8"),
        logging.StreamHandler(sys.stderr)
    ]
)
logger = logging.getLogger("mcp-scrum")

from mcp.server import Server
from mcp.server.stdio import stdio_server
from mcp.types import (
    Tool, TextContent, Resource, Prompt, PromptMessage,
    GetPromptResult, ListResourcesResult, ReadResourceResult,
    ListToolsResult, CallToolResult,
)

from client.api import ApiClient
from tools.sprint import register_sprint_tools
from tools.burndown import register_burndown_tools
from tools.task import register_task_tools
from tools.search import register_search_tools
from resources.board import register_resources
from prompts.standup import register_prompts


async def main():
    backend_url = os.getenv("BACKEND_URL", "http://localhost:8080/api/v1")
    username = os.getenv("BACKEND_USERNAME", "")
    password = os.getenv("BACKEND_PASSWORD", "")

    if not username or not password:
        raise ValueError("BACKEND_USERNAME 和 BACKEND_PASSWORD 环境变量必须设置")

    logger.info("MCP Scrum Standup Server (Python) 启动中...")
    logger.info(f"后端地址: {backend_url}")

    api = ApiClient(backend_url, username, password)
    server = Server("scrum-standup")

    # 注册所有 Tools / Resources / Prompts
    all_tools = []
    all_tools += register_sprint_tools(api)
    all_tools += register_burndown_tools(api)
    all_tools += register_task_tools(api)
    all_tools += register_search_tools(api)

    all_resources = register_resources(api)
    all_prompts = register_prompts()

    logger.info(f"已注册 {len(all_tools)} 个 Tool，{len(all_resources)} 个 Resource，{len(all_prompts)} 个 Prompt")

    # ── Tool 处理器 ──────────────────────────────────────────────────────────
    tool_handlers = {}
    for tool_def, handler in all_tools:
        tool_handlers[tool_def.name] = (tool_def, handler)

    @server.list_tools()
    async def list_tools() -> list[Tool]:
        return [t for t, _ in tool_handlers.values()]

    @server.call_tool()
    async def call_tool(name: str, arguments: dict) -> list[TextContent]:
        if name not in tool_handlers:
            logger.error(f"未知工具: {name}")
            raise ValueError(f"未知工具: {name}")
        _, handler = tool_handlers[name]
        logger.info(f"[TOOL CALL] 工具: {name}")
        logger.info(f"[TOOL CALL] 参数: {arguments}")
        try:
            result = await handler(arguments)
            logger.info(f"[TOOL CALL] 成功: {name}，返回长度: {len(result)}")
            return [TextContent(type="text", text=result)]
        except Exception as e:
            logger.error(f"[TOOL CALL] 失败: {name}，错误: {e}", exc_info=True)
            return [TextContent(type="text", text=f"错误: {str(e)}")]

    # ── Resource 处理器 ──────────────────────────────────────────────────────
    resource_handlers = {}
    for res_def, handler in all_resources:
        resource_handlers[res_def.uri] = (res_def, handler)

    @server.list_resources()
    async def list_resources() -> list[Resource]:
        return [r for r, _ in resource_handlers.values()]

    @server.read_resource()
    async def read_resource(uri: str) -> str:
        # 支持模板 URI 匹配
        for pattern, (res_def, handler) in resource_handlers.items():
            params = _match_uri(pattern, uri)
            if params is not None:
                logger.info(f"读取资源: {uri}")
                return await handler(params)
        raise ValueError(f"未知资源: {uri}")

    # ── Prompt 处理器 ────────────────────────────────────────────────────────
    prompt_handlers = {}
    for prompt_def, handler in all_prompts:
        prompt_handlers[prompt_def.name] = (prompt_def, handler)

    @server.list_prompts()
    async def list_prompts() -> list[Prompt]:
        return [p for p, _ in prompt_handlers.values()]

    @server.get_prompt()
    async def get_prompt(name: str, arguments: Optional[dict]) -> GetPromptResult:
        if name not in prompt_handlers:
            raise ValueError(f"未知 Prompt: {name}")
        _, handler = prompt_handlers[name]
        messages = await handler(arguments or {})
        return GetPromptResult(messages=messages)

    logger.info("MCP Server 已连接（stdio），等待请求...")
    async with stdio_server() as (read_stream, write_stream):
        await server.run(read_stream, write_stream, server.create_initialization_options())


def _match_uri(pattern: str, uri: str) -> Optional[dict]:
    """简单的 URI 模板匹配，支持 {param} 占位符"""
    import re
    regex = re.sub(r"\{(\w+)\}", r"(?P<\1>[^/]+)", re.escape(pattern))
    m = re.fullmatch(regex, uri)
    if m:
        return m.groupdict()
    return None


if __name__ == "__main__":
    asyncio.run(main())
