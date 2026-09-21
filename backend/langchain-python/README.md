# LangChain Python Service

该目录提供一个独立的 LangChain 服务（FastAPI），用于多 Agent 编排与 DeepSeek 调用。Spring Boot 将通过 HTTP 调用该服务。

## 目录结构
- `app/main.py`：FastAPI 入口
- `app/config.py`：配置与环境变量
- `app/llm.py`：DeepSeek LLM 初始化
- `app/agents.py`：多 Agent 角色与编排
- `app/tools.py`：工具定义（调用 Spring Boot API）
- `app/schemas.py`：请求/响应结构
- `requirements.txt`：依赖
- `.env.example`：环境变量示例

## 启动方式

```bash
pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --host 0.0.0.0 --port 8091
```

## 关键说明
- DeepSeek API 通过 `DEEPSEEK_API_KEY` 提供
- 业务数据通过 Spring Boot API 获取
- 可在此扩展 RAG/向量检索等能力
