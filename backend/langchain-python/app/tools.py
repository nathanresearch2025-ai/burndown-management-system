import asyncio
from typing import Dict, Any, Optional  # 类型注解
import aiohttp
from .config import settings  # 配置读取
import logging

# 配置日志
logger = logging.getLogger(__name__)

_async_session: Optional[aiohttp.ClientSession] = None
_session_lock = asyncio.Lock()


async def get_async_session() -> aiohttp.ClientSession:
    """获取单例 aiohttp ClientSession，支持连接池复用"""
    global _async_session
    if _async_session is not None and not _async_session.closed:
        return _async_session
    async with _session_lock:
        if _async_session is not None and not _async_session.closed:
            return _async_session
        # 配置连接池和超时
        timeout = aiohttp.ClientTimeout(total=30, connect=3, sock_read=20)
        connector = aiohttp.TCPConnector(limit=50, limit_per_host=20)
        _async_session = aiohttp.ClientSession(timeout=timeout, connector=connector)
        return _async_session


async def call_backend_tool(path: str, payload: Dict[str, Any], trace_id: Optional[str] = None) -> str:  # 统一封装对后端的工具调用
    url = f"{settings.backend_base_url}{path}"  # 拼接完整 API 地址

    logger.info(f"=== [Backend Tool Call] START ===")
    logger.info(f"URL: {url}")
    logger.info(f"Payload: {payload}")

    try:
        session = await get_async_session()
        headers = {"X-Trace-Id": trace_id, "Content-Type": "application/json"} if trace_id else {"Content-Type": "application/json"}

        async with session.post(url, json=payload, headers=headers) as resp:
            logger.info(f"Response status: {resp.status}")
            logger.info(f"Response headers: {dict(resp.headers)}")

            response_text = await resp.text()

            if resp.status >= 400:
                logger.error(f"HTTP error occurred: {resp.status} {resp.reason}")
                logger.error(f"Response body: {response_text}")
                logger.error(f"=== [Backend Tool Call] FAILED ===\n")
                raise aiohttp.ClientResponseError(
                    request_info=resp.request_info,
                    history=resp.history,
                    status=resp.status,
                    message=f"Server error '{resp.status} {resp.reason}' for url '{url}'",
                    headers=resp.headers
                )

            logger.info(f"Response body: {response_text[:500]}..." if len(response_text) > 500 else f"Response body: {response_text}")
            logger.info(f"=== [Backend Tool Call] SUCCESS ===\n")

            return response_text  # 返回响应正文（字符串）

    except aiohttp.ClientResponseError as e:
        logger.error(f"HTTP error occurred: {e}")
        logger.error(f"=== [Backend Tool Call] FAILED ===\n")
        raise
    except Exception as e:
        logger.error(f"Unexpected error: {e}")
        logger.error(f"=== [Backend Tool Call] FAILED ===\n")
        raise


async def get_in_progress_tasks(project_id: int, user_id: int, trace_id: Optional[str] = None) -> str:  # 获取进行中任务
    logger.info(f"[Tool] get_in_progress_tasks called - project_id={project_id}, user_id={user_id}")
    result = await call_backend_tool(  # 调用后端工具 API
        "/agent/tools/in-progress-tasks",  # 工具 API 路径
        {"projectId": project_id, "userId": user_id},  # 请求体
        trace_id=trace_id,
    )
    logger.info(f"[Tool] get_in_progress_tasks completed")
    return result


async def get_sprint_burndown(project_id: int, sprint_id: int, trace_id: Optional[str] = None) -> str:  # 获取燃尽图数据
    logger.info(f"[Tool] get_sprint_burndown called - project_id={project_id}, sprint_id={sprint_id}")
    result = await call_backend_tool(  # 调用后端工具 API
        "/agent/tools/sprint-burndown",  # 工具 API 路径
        {"projectId": project_id, "sprintId": sprint_id},  # 请求体
        trace_id=trace_id,
    )
    logger.info(f"[Tool] get_sprint_burndown completed")
    return result


async def evaluate_burndown_risk(project_id: int, sprint_id: int, trace_id: Optional[str] = None) -> str:  # 评估燃尽风险
    logger.info(f"[Tool] evaluate_burndown_risk called - project_id={project_id}, sprint_id={sprint_id}")
    result = await call_backend_tool(  # 调用后端工具 API
        "/agent/tools/burndown-risk",  # 工具 API 路径
        {"projectId": project_id, "sprintId": sprint_id},  # 请求体
        trace_id=trace_id,
    )
    logger.info(f"[Tool] evaluate_burndown_risk completed")
    return result
