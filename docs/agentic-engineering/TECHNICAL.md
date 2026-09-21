# 技术文档（简版）

## 1. 总体架构
系统维持 Spring Boot 主体，新增 AI Agent 编排层：

- Controller：`StandupAgentController` 或 `AiAgentController`
- Service：`StandupAgentService`（需求解析、规划、工具调用）
- Tools：`StandupTaskTools` / `StandupBurndownTools` / `StandupRiskTools`
- Memory：`AgentChatSession` / `AgentChatMessage`
- Monitoring：`StandupAgentMetrics` + Prometheus

## 2. 运行链路
用户输入 → Agent Service → 规划 → 工具调用 → 整合结果 → 持久化 → 返回结果

## 3. 关键模块映射
### 3.1 Agent 服务
- 入口：`StandupAgentService.query()`
- 功能：构建提示词 + Function Calling + 结果解析

### 3.2 工具层
- `getInProgressTasks`：基于 `Task` 表数据
- `getSprintBurndown`：基于 `BurndownPoint` 表
- `evaluateBurndownRisk`：根据燃尽曲线偏离判断风险

### 3.3 记忆与上下文
- Session：`AgentChatSession`
- Message：`AgentChatMessage`

### 3.4 监控
- Prometheus 采集：`/api/v1/actuator/prometheus`
- 告警规则：`backend/monitoring/alert_rules.yml`

## 4. 可选集成路径
### 4.1 Spring AI 模式（推荐）
- 由 Spring Boot 自行完成 Agent 编排
- LangChain 不参与

### 4.2 LangChain 独立服务模式
- Spring Boot 调用 LangChain 服务
- LangChain 通过工具 API 回调 Spring Boot

## 5. 技术约束
- 保持现有业务接口与数据层不变
- AI 编排层可扩展多 Agent 协作
- 所有调用必须具备 traceId、记录工具调用

