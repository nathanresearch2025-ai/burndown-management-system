# 日志增强文档

## 概述

为后端 Spring Boot 和 LangChain Python 项目添加了详细的日志打印功能，方便调试和问题排查。

## 修改内容

### 1. Spring Boot 后端日志增强

#### 1.1 LangchainToolController.java

**位置**: `backend/src/main/java/com/burndown/aiagent/standup/controller/LangchainToolController.java`

**修改内容**:
- 添加 `@Slf4j` 注解启用日志功能
- 为所有接口添加详细的请求/响应日志
- 支持 GET 和 POST 两种请求方式

**日志内容**:
- 接口调用开始标记
- 请求参数（body 和 query params）
- 最终处理参数
- 响应内容（超过200字符时截断）
- 接口调用结束标记

**示例日志输出**:
```
=== [LangchainToolController] /in-progress-tasks called ===
Request body: LangchainToolRequest(projectId=1, sprintId=null, userId=1)
Query params - projectId: null, userId: null
Final params - projectId: 1, userId: 1
Response: 当前没有进行中的任务
=== [LangchainToolController] /in-progress-tasks completed ===
```

#### 1.2 StandupTaskTools.java

**位置**: `backend/src/main/java/com/burndown/aiagent/standup/tool/StandupTaskTools.java`

**现有日志**:
- 工具调用日志（已存在）
- 错误日志（已存在）

#### 1.3 StandupBurndownTools.java

**位置**: `backend/src/main/java/com/burndown/aiagent/standup/tool/StandupBurndownTools.java`

**现有日志**:
- 工具调用日志（已存在）
- 错误日志（已存在）

#### 1.4 StandupRiskTools.java

**位置**: `backend/src/main/java/com/burndown/aiagent/standup/tool/StandupRiskTools.java`

**现有日志**:
- 工具调用日志（已存在）
- 错误日志（已存在）

### 2. LangChain Python 项目日志增强

#### 2.1 main.py

**位置**: `backend/langchain-python/app/main.py`

**修改内容**:
- 配置全局日志格式
- 为 `/agent/standup/query` 接口添加详细日志
- 添加异常捕获和错误日志

**日志内容**:
- 请求接收日志（包含所有请求参数）
- 请求处理成功日志
- 响应摘要长度统计
- 异常错误日志

**示例日志输出**:
```
================================================================================
[FastAPI] /agent/standup/query endpoint called
Request data: question=我今天有哪些任务?, projectId=1, sprintId=1, userId=1
================================================================================
```

#### 2.2 tools.py

**位置**: `backend/langchain-python/app/tools.py`

**修改内容**:
- 添加 logging 模块
- 为 `call_backend_tool` 函数添加详细的 HTTP 请求/响应日志
- 为每个工具函数添加调用日志
- 添加异常捕获和错误日志

**日志内容**:
- HTTP 请求 URL
- 请求 payload
- 响应状态码
- 响应头
- 响应体（超过500字符时截断）
- HTTP 错误详情
- 工具函数调用开始/结束标记

**示例日志输出**:
```
=== [Backend Tool Call] START ===
URL: http://localhost:8080/api/v1/agent/tools/in-progress-tasks
Payload: {'projectId': 1, 'userId': 1}
Response status: 200
Response body: 当前没有进行中的任务
=== [Backend Tool Call] SUCCESS ===

[Tool] get_in_progress_tasks called - project_id=1, user_id=1
[Tool] get_in_progress_tasks completed
```

#### 2.3 llm.py

**位置**: `backend/langchain-python/app/llm.py`

**修改内容**:
- 添加 logging 模块
- 为 `build_llm` 函数添加 LLM 实例构建日志

**日志内容**:
- 模型名称
- API 基础 URL

**示例日志输出**:
```
[LLM] Building LLM instance - model=deepseek-chat, base_url=https://api.deepseek.com
```

#### 2.4 agents.py

**位置**: `backend/langchain-python/app/agents.py`

**现有日志**:
- 多 Agent 流水线开始/结束标记（已存在）
- 每个 Agent 步骤的调用日志（已存在）
- 工具调用的 DEBUG 日志（已存在）

## 日志级别说明

### Spring Boot 后端
- **INFO**: 正常的接口调用、参数、响应
- **WARN**: 数据未找到等警告情况
- **ERROR**: 异常错误

### LangChain Python
- **INFO**: 正常的请求处理、工具调用、LLM 调用
- **ERROR**: HTTP 错误、异常错误

## 日志配置

### Spring Boot
日志配置在 `application.yml` 中：
```yaml
logging:
  level:
    com.burndown.aiagent: INFO
```

### Python
日志配置在 `main.py` 中：
```python
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
```

## 调试建议

1. **查看完整请求链路**:
   - FastAPI 接收请求 → 多 Agent 编排 → 工具调用 → Spring Boot 接口 → 数据库查询

2. **关键日志点**:
   - FastAPI 入口: `[FastAPI] /agent/standup/query endpoint called`
   - 工具调用: `[Backend Tool Call] START`
   - Spring Boot 接口: `[LangchainToolController] /xxx called`
   - 工具执行: `Tool called: xxx`

3. **错误排查**:
   - 检查 HTTP 状态码和响应体
   - 检查请求参数是否正确传递
   - 检查异常堆栈信息

## 后续优化建议

1. 添加请求 ID 追踪（分布式追踪）
2. 添加性能监控（响应时间统计）
3. 添加日志聚合（ELK/Loki）
4. 添加日志采样（高流量场景）
5. 敏感信息脱敏（API Key、密码等）
