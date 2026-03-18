"""
FastAPI 应用启动脚本
用于 PyCharm 直接运行
"""
import uvicorn
from dotenv import load_dotenv

# 加载环境变量
load_dotenv()

if __name__ == "__main__":
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=8091,
        reload=True,
        log_level="info"
    )
