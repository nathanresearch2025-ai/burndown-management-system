import requests  # HTTP 客户端
from typing import Dict, Any  # 类型注解
from .config import settings  # 配置读取
import logging

# 配置日志
logger = logging.getLogger(__name__)


def call_backend_tool(path: str, payload: Dict[str, Any]) -> str:  # 统一封装对后端的工具调用
    url = f"{settings.backend_base_url}{path}"  # 拼接完整 API 地址

    logger.info(f"=== [Backend Tool Call] START ===")
    logger.info(f"URL: {url}")
    logger.info(f"Payload: {payload}")

    try:
        resp = requests.post(url, json=payload, timeout=20)  # 发送 POST 请求

        logger.info(f"Response status: {resp.status_code}")
        logger.info(f"Response headers: {dict(resp.headers)}")

        resp.raise_for_status()  # 若状态码异常则抛出

        response_text = resp.text
        logger.info(f"Response body: {response_text[:500]}..." if len(response_text) > 500 else f"Response body: {response_text}")
        logger.info(f"=== [Backend Tool Call] SUCCESS ===\n")

        return response_text  # 返回响应正文（字符串）

    except requests.exceptions.HTTPError as e:
        logger.error(f"HTTP error occurred: {e}")
        logger.error(f"Response body: {e.response.text}")
        logger.error(f"=== [Backend Tool Call] FAILED ===\n")
        raise
    except Exception as e:
        logger.error(f"Unexpected error: {e}")
        logger.error(f"=== [Backend Tool Call] FAILED ===\n")
        raise


def get_in_progress_tasks(project_id: int, user_id: int) -> str:  # 获取进行中任务
    logger.info(f"[Tool] get_in_progress_tasks called - project_id={project_id}, user_id={user_id}")
    result = call_backend_tool(  # 调用后端工具 API
        "/agent/tools/in-progress-tasks",  # 工具 API 路径
        {"projectId": project_id, "userId": user_id},  # 请求体
    )
    logger.info(f"[Tool] get_in_progress_tasks completed")
    return result


def get_sprint_burndown(project_id: int, sprint_id: int) -> str:  # 获取燃尽图数据
    logger.info(f"[Tool] get_sprint_burndown called - project_id={project_id}, sprint_id={sprint_id}")
    result = call_backend_tool(  # 调用后端工具 API
        "/agent/tools/sprint-burndown",  # 工具 API 路径
        {"projectId": project_id, "sprintId": sprint_id},  # 请求体
    )
    logger.info(f"[Tool] get_sprint_burndown completed")
    return result


def evaluate_burndown_risk(project_id: int, sprint_id: int) -> str:  # 评估燃尽风险
    logger.info(f"[Tool] evaluate_burndown_risk called - project_id={project_id}, sprint_id={sprint_id}")
    result = call_backend_tool(  # 调用后端工具 API
        "/agent/tools/burndown-risk",  # 工具 API 路径
        {"projectId": project_id, "sprintId": sprint_id},  # 请求体
    )
    logger.info(f"[Tool] evaluate_burndown_risk completed")
    return result
