from pydantic import BaseModel  # Pydantic 基类，用于定义配置结构并自动校验
from dotenv import load_dotenv  # 读取 .env 文件的工具
import os  # 访问环境变量

load_dotenv()  # 加载当前目录下的 .env 文件（如果存在）


class Settings(BaseModel):  # 配置对象，集中管理环境变量
    deepseek_api_key: str = os.getenv("DEEPSEEK_API_KEY", "")  # DeepSeek API Key
    deepseek_base_url: str = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")  # DeepSeek 接口地址
    deepseek_chat_model: str = os.getenv("DEEPSEEK_CHAT_MODEL", "deepseek-chat")  # DeepSeek 模型名称

    db_host: str = os.getenv("DB_HOST", "")  # 数据库主机
    db_port: int = int(os.getenv("DB_PORT", "5432"))  # 数据库端口
    db_name: str = os.getenv("DB_NAME", "")  # 数据库名称
    db_user: str = os.getenv("DB_USER", "")  # 数据库用户名
    db_password: str = os.getenv("DB_PASSWORD", "")  # 数据库密码

    backend_base_url: str = os.getenv("BACKEND_BASE_URL", "http://localhost:8080/api/v1")  # Spring Boot API 基础地址
    service_port: int = int(os.getenv("LANGCHAIN_SERVICE_PORT", "8091"))  # 本服务端口


settings = Settings()  # 实例化配置对象，供全局引用
