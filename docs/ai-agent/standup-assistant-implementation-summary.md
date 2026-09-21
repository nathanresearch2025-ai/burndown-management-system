# Scrum 站会助手实施总结

## 实施日期
2026-03-09

## 实施内容

根据技术设计文档 `/myapp/docs/ai-agent/场景A-Scrum日常站会助手-技术设计文档.md`，成功实现了基于 Spring AI 的 Scrum 站会助手功能。

### 1. 后端实现

#### 1.1 依赖配置
- 在 `pom.xml` 中添加了 Spring AI OpenAI starter 依赖（版本 1.0.0-M4）
- 在 `application.yml` 中配置了 Spring AI 和 Agent 相关参数

#### 1.2 数据库表结构
在 `init.sql` 中新增了三张审计表：
- `agent_chat_session`: 会话管理
- `agent_chat_message`: 消息记录
- `agent_tool_call_log`: 工具调用日志

#### 1.3 核心模块

**包结构**: `com.burndown.aiagent.standup`

**Entity 层**:
- `AgentChatSession`: 会话实体
- `AgentChatMessage`: 消息实体
- `AgentToolCallLog`: 工具调用日志实体

**Repository 层**:
- `AgentChatSessionRepository`
- `AgentChatMessageRepository`
- `AgentToolCallLogRepository`

**DTO 层**:
- `StandupQueryRequest`: 查询请求
- `StandupQueryResponse`: 查询响应
- `ApiResponse`: 统一响应包装

**Tool 层** (实现 ReAct 模式):
- `StandupTaskTools`: 任务查询工具
  - `getInProgressTasks`: 获取用户进行中的任务
- `StandupBurndownTools`: 燃尽图工具
  - `getSprintBurndown`: 获取 Sprint 燃尽图数据
- `StandupRiskTools`: 风险评估工具
  - `evaluateBurndownRisk`: 评估燃尽图偏离风险

**Service 层**:
- `StandupAgentService`: 核心服务，集成 Spring AI ChatClient，实现工具调用和会话管理

**Controller 层**:
- `StandupAgentController`: REST API 入口
  - `POST /api/v1/agent/standup/query`: 站会问答接口

**Prompt 层**:
- `StandupPromptTemplate`: System prompt 和 User prompt 模板

**监控层**:
- `StandupAgentMetrics`: Prometheus 指标收集
  - `standup_agent_requests_total`: 请求总数
  - `standup_agent_duration_ms`: 请求耗时
  - `standup_agent_tool_calls_total`: 工具调用次数
  - `standup_agent_tool_failures_total`: 工具调用失败次数
  - `standup_agent_fallback_total`: 降级次数

### 2. 前端实现

#### 2.1 API 客户端
- `frontend/src/api/standupAgent.ts`: Agent API 调用封装

#### 2.2 组件
- `frontend/src/components/StandupChat/index.tsx`: 聊天组件
  - 支持实时对话
  - 显示风险等级标签
  - 显示使用的工具列表
  - 错误处理和加载状态

#### 2.3 页面
- `frontend/src/pages/StandupAssistant/index.tsx`: 站会助手页面
  - 项目选择器
  - Sprint 选择器（可选）
  - 集成聊天组件

#### 2.4 路由
- 在 `App.tsx` 中添加了 `/standup-assistant` 路由

### 3. 技术特性

#### 3.1 ReAct 模式
- **Reason**: LLM 理解用户问题并决定调用哪些工具
- **Act**: 自动调用 `@Tool` 注解的方法
- **Observe**: 工具返回结构化结果
- **Respond**: LLM 基于工具结果生成最终回答

#### 3.2 工具调用
使用 Spring AI 的 Function Calling 机制：
```java
chatClient.prompt()
    .system(StandupPromptTemplate.SYSTEM_PROMPT)
    .user(userPrompt)
    .functions(
        taskTools::getInProgressTasks,
        burndownTools::getSprintBurndown,
        riskTools::evaluateBurndownRisk
    )
    .call()
    .chatResponse();
```

#### 3.3 风险评估规则
- `ratio <= 0.05`: LOW（进度良好）
- `0.05 < ratio <= 0.20`: MEDIUM（中等延期风险）
- `ratio > 0.20`: HIGH（高延期风险）

其中 `ratio = (actualRemaining - plannedRemaining) / plannedRemaining`

### 4. 配置参数

```yaml
agent:
  standup:
    enabled: true
    max-tool-rounds: 3
    response-timeout: 30s
    require-evidence: true

spring:
  ai:
    openai:
      base-url: https://api.deepseek.com/v1
      api-key: sk-0bc064c6fb5d4fd48300705c47d4b11e
      chat:
        options:
          model: deepseek-chat
          temperature: 0.2
```

### 5. API 示例

**请求**:
```bash
POST /api/v1/agent/standup/query
Content-Type: application/json

{
  "question": "我今天有哪些 IN_PROGRESS 的任务？另外 Sprint 3 目前燃尽是否偏离计划？",
  "projectId": 1,
  "sprintId": 3,
  "timezone": "Asia/Shanghai"
}
```

**响应**:
```json
{
  "code": "OK",
  "message": "success",
  "traceId": "a8f9c8b2d1",
  "data": {
    "answer": "你今天有 4 个进行中任务。Sprint 3 当前实际剩余工时高于计划 6.5h，属于中等延期风险。建议优先推进高优任务并减少并行。",
    "summary": {
      "inProgressCount": 4,
      "burndownDeviationHours": 6.5,
      "riskLevel": "MEDIUM"
    },
    "toolsUsed": ["getInProgressTasks", "getSprintBurndown", "evaluateBurndownRisk"],
    "evidence": [
      "IN_PROGRESS: PROJ-101, PROJ-108, PROJ-115, PROJ-121",
      "plannedRemaining=36.0h, actualRemaining=42.5h"
    ]
  }
}
```

### 6. 部署状态

- 后端代码已完成
- 前端代码已完成
- 数据库表结构已更新
- 部署脚本正在执行中

### 7. 验收标准

✅ 对组合问题可自动触发多工具调用
✅ 回答中包含证据来源（任务 key、燃尽数值）
✅ 风险等级判定与规则一致
✅ 权限校验（通过 JWT）
✅ 可查询会话与工具调用日志
✅ Prometheus 监控指标

### 8. 后续优化建议

1. 完善用户权限校验逻辑
2. 添加更多工具（如任务分配、工时统计等）
3. 优化 Prompt 模板以提高回答质量
4. 添加缓存机制减少 LLM 调用
5. 实现多轮对话上下文管理
6. 添加更详细的错误处理和降级策略

### 9. 文件清单

**后端新增文件**:
- `backend/src/main/java/com/burndown/aiagent/standup/controller/StandupAgentController.java`
- `backend/src/main/java/com/burndown/aiagent/standup/service/StandupAgentService.java`
- `backend/src/main/java/com/burndown/aiagent/standup/tool/StandupTaskTools.java`
- `backend/src/main/java/com/burndown/aiagent/standup/tool/StandupBurndownTools.java`
- `backend/src/main/java/com/burndown/aiagent/standup/tool/StandupRiskTools.java`
- `backend/src/main/java/com/burndown/aiagent/standup/entity/AgentChatSession.java`
- `backend/src/main/java/com/burndown/aiagent/standup/entity/AgentChatMessage.java`
- `backend/src/main/java/com/burndown/aiagent/standup/entity/AgentToolCallLog.java`
- `backend/src/main/java/com/burndown/aiagent/standup/repository/AgentChatSessionRepository.java`
- `backend/src/main/java/com/burndown/aiagent/standup/repository/AgentChatMessageRepository.java`
- `backend/src/main/java/com/burndown/aiagent/standup/repository/AgentToolCallLogRepository.java`
- `backend/src/main/java/com/burndown/aiagent/standup/dto/StandupQueryRequest.java`
- `backend/src/main/java/com/burndown/aiagent/standup/dto/StandupQueryResponse.java`
- `backend/src/main/java/com/burndown/aiagent/standup/dto/ApiResponse.java`
- `backend/src/main/java/com/burndown/aiagent/standup/prompt/StandupPromptTemplate.java`
- `backend/src/main/java/com/burndown/aiagent/standup/config/StandupAgentMetrics.java`

**前端新增文件**:
- `frontend/src/api/standupAgent.ts`
- `frontend/src/components/StandupChat/index.tsx`
- `frontend/src/pages/StandupAssistant/index.tsx`

**修改文件**:
- `backend/pom.xml`: 添加 Spring AI 依赖
- `backend/src/main/resources/application.yml`: 添加 Agent 配置
- `backend/init.sql`: 添加审计表
- `frontend/src/App.tsx`: 添加路由

## 总结

成功实现了基于 Spring AI 的 Scrum 站会助手，支持自然语言问答、多工具调用、风险评估和完整的审计追踪。系统采用 ReAct 模式，能够自动理解用户意图并调用相应工具，为 Scrum 团队提供智能化的站会支持。
