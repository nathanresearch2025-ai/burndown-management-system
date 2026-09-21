#!/usr/bin/env python
"""
快速测试脚本 - 验证 MCP Server 能否正常启动和注册工具
"""

import asyncio
import os
import sys

# 添加当前目录到 Python 路径
sys.path.insert(0, os.path.dirname(__file__))

from dotenv import load_dotenv
from client.api import ApiClient
from tools.sprint import register_sprint_tools
from tools.burndown import register_burndown_tools
from tools.task import register_task_tools
from tools.search import register_search_tools
from resources.board import register_resources
from prompts.standup import register_prompts

load_dotenv()


async def test():
    backend_url = os.getenv("BACKEND_URL", "http://localhost:8080/api/v1")
    username = os.getenv("BACKEND_USERNAME", "admin")
    password = os.getenv("BACKEND_PASSWORD", "password123")

    print(f"后端地址: {backend_url}")
    print(f"用户名: {username}")

    api = ApiClient(backend_url, username, password)

    # 注册所有组件
    all_tools = []
    all_tools += register_sprint_tools(api)
    all_tools += register_burndown_tools(api)
    all_tools += register_task_tools(api)
    all_tools += register_search_tools(api)

    all_resources = register_resources(api)
    all_prompts = register_prompts()

    print(f"\n[OK] 已注册 {len(all_tools)} 个 Tool")
    print(f"[OK] 已注册 {len(all_resources)} 个 Resource")
    print(f"[OK] 已注册 {len(all_prompts)} 个 Prompt")

    print("\n工具列表:")
    for tool_def, _ in all_tools:
        print(f"  - {tool_def.name}: {tool_def.description}")

    # 测试后端连接
    print("\n测试后端连接...")
    try:
        projects = await api.get("/projects")
        print(f"[OK] 后端连接成功，获取到 {len(projects)} 个项目")
    except Exception as e:
        print(f"[ERROR] 后端连接失败: {e}")

    await api.close()
    print("\n测试完成！")


if __name__ == "__main__":
    asyncio.run(test())
