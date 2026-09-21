# asyncio：Python 标准异步库，提供事件循环、协程、锁等原语
import asyncio
# Dict/Any/Optional：类型注解工具，Dict[str,Any] 表示任意字典，Optional[X] 表示 X 或 None
from typing import Dict, Any, Optional
# aiohttp：异步 HTTP 客户端库，支持连接池复用，适合高并发场景
import aiohttp
# settings：全局配置单例，提供 backend_base_url 等配置项
from .config import settings
import logging

logger = logging.getLogger(__name__)

# 模块级全局变量：存放唯一的 aiohttp 会话实例（连接池单例）
# 初始为 None，首次调用 get_async_session() 时懒初始化
_async_session: Optional[aiohttp.ClientSession] = None

# 异步锁：防止多个协程同时初始化连接池（双重检查锁定模式中的锁）
_session_lock = asyncio.Lock()


async def get_async_session() -> aiohttp.ClientSession:
    """获取单例 aiohttp ClientSession，支持连接池复用。
    使用双重检查锁定（DCL）模式：先无锁判断，再加锁二次确认，兼顾性能与安全。
    """
    # global 声明：函数内对模块级变量赋值必须先声明 global，否则会创建局部变量
    global _async_session

    # 第一次检查（无锁，快速路径）：会话已存在且未关闭则直接返回，高并发时大多数请求走这里
    if _async_session is not None and not _async_session.closed:
        return _async_session

    # 加锁：防止多个协程同时通过第一次检查后重复创建会话
    async with _session_lock:
        # 第二次检查（加锁后）：可能在等锁期间已被其他协程初始化完毕
        if _async_session is not None and not _async_session.closed:
            return _async_session

        # 配置请求超时：total=整体超时30s，connect=TCP握手3s快速失败，sock_read=读响应体20s
        timeout = aiohttp.ClientTimeout(total=30, connect=3, sock_read=20)

        # 配置连接池：limit=全局最大并发连接50，limit_per_host=对同一host最多20个并发连接
        connector = aiohttp.TCPConnector(limit=50, limit_per_host=20)

        # 创建会话，注入超时和连接池配置
        _async_session = aiohttp.ClientSession(timeout=timeout, connector=connector)
        return _async_session


async def call_backend_tool(path: str, payload: Dict[str, Any], trace_id: Optional[str] = None) -> str:
    """统一封装对 Spring Boot 后端工具接口的 HTTP POST 调用。
    所有三个工具函数（get_in_progress_tasks、get_sprint_burndown、evaluate_burndown_risk）
    都通过此函数发起请求，统一处理日志、错误和响应解析。
    """
    # 拼接完整 URL，例如：http://localhost:8080/api/v1/agent/tools/sprint-burndown
    url = f"{settings.backend_base_url}{path}"

    logger.info(f"=== [Backend Tool Call] START ===")
    logger.info(f"URL: {url}")
    logger.info(f"Payload: {payload}")

    try:
        # 获取复用的连接池会话
        session = await get_async_session()

        # 如果调用方传入 trace_id，则在 Header 中带上 X-Trace-Id，便于 Spring Boot 日志关联同一链路
        headers = {"X-Trace-Id": trace_id, "Content-Type": "application/json"} if trace_id else {"Content-Type": "application/json"}

        # async with：确保响应连接在使用完后正确归还连接池
        # json=payload：自动将 dict 序列化为 JSON 字符串并设置 Content-Type
        async with session.post(url, json=payload, headers=headers) as resp:
            logger.info(f"Response status: {resp.status}")
            logger.info(f"Response headers: {dict(resp.headers)}")

            # 读取完整响应体文本（异步，不阻塞事件循环）
            response_text = await resp.text()

            # aiohttp 默认不对 4xx/5xx 自动抛异常，需手动判断并主动 raise
            if resp.status >= 400:
                logger.error(f"HTTP error occurred: {resp.status} {resp.reason}")
                logger.error(f"Response body: {response_text}")
                logger.error(f"=== [Backend Tool Call] FAILED ===\n")
                # 构造标准 aiohttp 异常，携带请求信息、状态码和错误消息
                raise aiohttp.ClientResponseError(
                    request_info=resp.request_info,  # 请求元信息（URL、method、headers）
                    history=resp.history,             # 重定向历史
                    status=resp.status,               # HTTP 状态码
                    message=f"Server error '{resp.status} {resp.reason}' for url '{url}'",
                    headers=resp.headers              # 响应 headers
                )

            # 响应体超过 500 字符时截断日志，避免刷屏
            logger.info(f"Response body: {response_text[:500]}..." if len(response_text) > 500 else f"Response body: {response_text}")
            logger.info(f"=== [Backend Tool Call] SUCCESS ===\n")

            # 将响应正文字符串返回给调用方（工具函数直接返回给 LangChain Agent）
            return response_text

    except aiohttp.ClientResponseError as e:
        # HTTP 4xx/5xx 错误：已在上面记录详细日志，这里直接向上抛出
        logger.error(f"HTTP error occurred: {e}")
        logger.error(f"=== [Backend Tool Call] FAILED ===\n")
        raise
    except Exception as e:
        # 其他异常（网络超时、DNS 解析失败等）：记录后向上抛出
        logger.error(f"Unexpected error: {e}")
        logger.error(f"=== [Backend Tool Call] FAILED ===\n")
        raise


async def get_in_progress_tasks(project_id: int, user_id: int, trace_id: Optional[str] = None) -> str:
    """获取指定项目中指定用户当前进行中（IN_PROGRESS）的任务列表。
    调用 Spring Boot 后端接口：POST /agent/tools/in-progress-tasks
    """
    logger.info(f"[Tool] get_in_progress_tasks called - project_id={project_id}, user_id={user_id}")
    result = await call_backend_tool(
        "/agent/tools/in-progress-tasks",          # Spring Boot 工具接口路径
        {"projectId": project_id, "userId": user_id},  # 请求体：项目 ID + 用户 ID
        trace_id=trace_id,
    )
    logger.info(f"[Tool] get_in_progress_tasks completed")
    return result


async def get_sprint_burndown(project_id: int, sprint_id: int, trace_id: Optional[str] = None) -> str:
    """获取指定 Sprint 的燃尽图数据点（每日剩余故事点）。
    调用 Spring Boot 后端接口：POST /agent/tools/sprint-burndown
    """
    logger.info(f"[Tool] get_sprint_burndown called - project_id={project_id}, sprint_id={sprint_id}")
    result = await call_backend_tool(
        "/agent/tools/sprint-burndown",                        # Spring Boot 工具接口路径
        {"projectId": project_id, "sprintId": sprint_id},     # 请求体：项目 ID + Sprint ID
        trace_id=trace_id,
    )
    logger.info(f"[Tool] get_sprint_burndown completed")
    return result


async def evaluate_burndown_risk(project_id: int, sprint_id: int, trace_id: Optional[str] = None) -> str:
    """评估指定 Sprint 的燃尽风险等级（LOW/MEDIUM/HIGH）及原因。
    调用 Spring Boot 后端接口：POST /agent/tools/burndown-risk
    """
    logger.info(f"[Tool] evaluate_burndown_risk called - project_id={project_id}, sprint_id={sprint_id}")
    result = await call_backend_tool(
        "/agent/tools/burndown-risk",                          # Spring Boot 工具接口路径
        {"projectId": project_id, "sprintId": sprint_id},     # 请求体：项目 ID + Sprint ID
        trace_id=trace_id,
    )
    logger.info(f"[Tool] evaluate_burndown_risk completed")
    return result
