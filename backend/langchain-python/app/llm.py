# ChatOpenAI：LangChain 官方的 OpenAI 兼容客户端
# 通过设置 base_url 可对接任何 OpenAI 协议兼容服务（DeepSeek、Ollama、vLLM 等）
from langchain_openai import ChatOpenAI
# settings：全局配置单例，读取 API Key、模型名、接口地址等环境变量
from .config import settings
import logging
# lru_cache：标准库缓存装饰器，相同参数的调用只执行一次函数体，后续直接返回缓存结果
from functools import lru_cache

logger = logging.getLogger(__name__)


# maxsize=1：因为 build_llm 无参数，缓存 key 固定，整个进程生命周期只创建一个 LLM 实例
# 避免每次请求重复构建 HTTP 客户端对象，节省资源
@lru_cache(maxsize=1)
def build_llm():  # 构建并返回全局复用的 LLM 客户端实例
    # 防御性检查：未配置 API Key 时在构建阶段即刻报错，比调用 API 时才报错更易排查
    if not settings.deepseek_api_key:
        raise ValueError("DEEPSEEK_API_KEY is required")

    logger.info(f"[LLM] Building LLM instance - model={settings.deepseek_chat_model}, "
                f"base_url={settings.deepseek_base_url}")

    return ChatOpenAI(
        model=settings.deepseek_chat_model,   # 传给 API 的 model 参数，如 "deepseek-chat"
        base_url=settings.deepseek_base_url,  # 替换默认的 api.openai.com，指向 DeepSeek 或私有部署
        api_key=settings.deepseek_api_key,    # Bearer Token 鉴权密钥
        temperature=0.2,  # 生成随机性控制：0=完全确定，1=高随机；0.2 适合需要结构化 JSON 输出的场景
    )
