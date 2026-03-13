import httpx  # HTTP 客户端
from typing import Dict, Any  # 类型注解
from .config import settings  # 配置读取


def call_backend_tool(path: str, payload: Dict[str, Any]) -> str:  # 统一封装对后端的工具调用
    url = f"{settings.backend_base_url}{path}"  # 拼接完整 API 地址
    with httpx.Client(timeout=20) as client:  # 创建同步 HTTP 客户端
        resp = client.post(url, json=payload)  # 发送 POST 请求
        resp.raise_for_status()  # 若状态码异常则抛出
        return resp.text  # 返回响应正文（字符串）


def get_in_progress_tasks(project_id: int, user_id: int) -> str:  # 获取进行中任务
    return call_backend_tool(  # 调用后端工具 API
        "/agent/tools/in-progress-tasks",  # 工具 API 路径
        {"projectId": project_id, "userId": user_id},  # 请求体
    )


def get_sprint_burndown(project_id: int, sprint_id: int) -> str:  # 获取燃尽图数据
    return call_backend_tool(  # 调用后端工具 API
        "/agent/tools/sprint-burndown",  # 工具 API 路径
        {"projectId": project_id, "sprintId": sprint_id},  # 请求体
    )


def evaluate_burndown_risk(project_id: int, sprint_id: int) -> str:  # 评估燃尽风险
    return call_backend_tool(  # 调用后端工具 API
        "/agent/tools/burndown-risk",  # 工具 API 路径
        {"projectId": project_id, "sprintId": sprint_id},  # 请求体
    )
