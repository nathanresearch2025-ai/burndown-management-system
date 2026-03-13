from langchain_openai import ChatOpenAI  # LangChain 的 OpenAI 兼容客户端（可接 DeepSeek）
from .config import settings  # 引入全局配置


def build_llm():  # 构建 LLM 客户端实例
    if not settings.deepseek_api_key:  # 未配置 API Key 时直接报错
        raise ValueError("DEEPSEEK_API_KEY is required")  # 提示必须设置密钥

    return ChatOpenAI(  # 返回 LangChain 的 LLM 实例
        model=settings.deepseek_chat_model,  # 使用的模型名称
        base_url=settings.deepseek_base_url,  # DeepSeek API 地址
        api_key=settings.deepseek_api_key,  # DeepSeek API Key
        temperature=0.2,  # 控制生成随机性
    )
