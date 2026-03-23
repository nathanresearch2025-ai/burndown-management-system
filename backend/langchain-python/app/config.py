# Pydantic BaseModel：数据验证基类，字段类型不匹配时自动抛出 ValidationError
from pydantic import BaseModel
# load_dotenv：读取项目根目录下的 .env 文件，将键值对注入当前进程的 os.environ
from dotenv import load_dotenv
# os：标准库，用于通过 os.getenv() 读取进程环境变量
import os

# 模块加载时立即执行：扫描当前工作目录的 .env 文件并注入环境变量
# 若 .env 不存在则静默跳过，不报错
load_dotenv()


class Settings(BaseModel):
    """全局配置对象，集中管理所有环境变量。
    继承 BaseModel 后，Pydantic 会在实例化时对所有字段做类型校验。
    """

    # DeepSeek API Key，用于 Bearer Token 鉴权
    # 未配置时为空字符串，build_llm() 会检测并主动抛出异常
    deepseek_api_key: str = os.getenv("DEEPSEEK_API_KEY", "")

    # DeepSeek 接口地址，默认指向官方地址
    # 可替换为任何 OpenAI 协议兼容的地址（Ollama、vLLM、Azure OpenAI 等）
    deepseek_base_url: str = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")

    # 使用的模型名称，默认 deepseek-chat
    # 可换成 deepseek-reasoner、gpt-4o 等任何 OpenAI 兼容模型
    deepseek_chat_model: str = os.getenv("DEEPSEEK_CHAT_MODEL", "deepseek-chat")

    # PostgreSQL 主机地址（当前代码中未直接使用，工具调用走 Spring Boot HTTP）
    db_host: str = os.getenv("DB_HOST", "")

    # PostgreSQL 端口，os.getenv 返回字符串，int() 强转为整型
    db_port: int = int(os.getenv("DB_PORT", "5432"))

    # 数据库名称
    db_name: str = os.getenv("DB_NAME", "")

    # 数据库用户名
    db_user: str = os.getenv("DB_USER", "")

    # 数据库密码
    db_password: str = os.getenv("DB_PASSWORD", "")

    # Spring Boot 后端 API 根地址，tools.py 中所有 HTTP 调用都拼接在此后面
    backend_base_url: str = os.getenv("BACKEND_BASE_URL", "http://localhost:8080/api/v1")

    # 本 Python 服务的监听端口，默认 8091
    service_port: int = int(os.getenv("LANGCHAIN_SERVICE_PORT", "8091"))


# 模块级单例：config.py 被导入时立即实例化
# 其他模块通过 `from .config import settings` 共享同一个对象，避免重复读取环境变量
settings = Settings()
