import asyncio
from typing import Dict, Any, Optional  # 类型注解
import httpx
from .config import settings  # 配置读取
import logging

# 配置日志
logger = logging.getLogger(__name__)

_async_client: Optional[httpx.AsyncClient] = None
_client_lock = asyncio.Lock()


async def get_async_client() -> httpx.AsyncClient:
    global _async_client
    if _async_client is not None:
        return _async_client
    async with _client_lock:
        if _async_client is not None:
            return _async_client
        _async_client = httpx.AsyncClient(
            timeout=httpx.Timeout(connect=3.0, read=20.0, write=10.0, pool=3.0),
            limits=httpx.Limits(max_connections=50, max_keepalive_connections=20),
        )
        return _async_client


async def call_backend_tool(path: str, payload: Dict[str, Any], trace_id: Optional[str] = None) -> str:  # 统一封装对后端的工具调用
    url = f"{settings.backend_base_url}{path}"  # 拼接完整 API 地址

    logger.info(f"=== [Backend Tool Call] START ===")
    logger.info(f"URL: {url}")
    logger.info(f"Payload: {payload}")

    try:
        client = await get_async_client()
        headers = {"X-Trace-Id": trace_id} if trace_id else None
        resp = await client.post(url, json=payload, headers=headers)

        logger.info(f"Response status: {resp.status_code}")
        logger.info(f"Response headers: {dict(resp.headers)}")

        resp.raise_for_status()  # 若状态码异常则抛出

        response_text = resp.text
        logger.info(f"Response body: {response_text[:500]}..." if len(response_text) > 500 else f"Response body: {response_text}")
        logger.info(f"=== [Backend Tool Call] SUCCESS ===\n")

        return response_text  # 返回响应正文（字符串）

    except httpx.HTTPStatusError as e:
        logger.error(f"HTTP error occurred: {e}")
        logger.error(f"Response body: {e.response.text if e.response is not None else ''}")
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
