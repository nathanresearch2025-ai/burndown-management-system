# Service 和 Tool 类详细说明文档

## 📋 目录

1. [AI Agent Tool 类](#ai-agent-tool-类)
2. [AI Agent Service 类](#ai-agent-service-类)
3. [核心 Service 类](#核心-service-类)
4. [AI 相关 Service 类](#ai-相关-service-类)

---

## 🛠️ AI Agent Tool 类

### 1. StandupTaskTools.java
**路径**: `backend/src/main/java/com/burndown/aiagent/standup/tool/StandupTaskTools.java`

**功能说明**:
提供给 AI Agent 调用的任务查询工具

**核心方法**:
- `getInProgressTasks(GetInProgressTasksRequest request)`: 获取用户当前进行中的任务列表

**工作原理**:
1. 查询指定项目中，指定用户的所有"进行中"状态的任务
2. 返回任务的关键信息：任务编号、标题、优先级、故事点、更新时间
3. AI 根据 `@Description` 注解决定是否调用此工具

**AI 调用时机**:
- 用户询问"我有哪些任务在做"
- 用户询问"今天的工作进展"
- 用户询问"我负责的任务"

**返回格式**:
```
找到 3 个进行中的任务：
- TASK-101: 实现用户登录功能 (优先级: HIGH, 故事点: 5, 更新时间: 2026-03-10)
- TASK-102: 优化数据库查询 (优先级: MEDIUM, 故事点: 3, 更新时间: 2026-03-09)
- TASK-103: 修复前端样式 (优先级: LOW, 故事点: 2, 更新时间: 2026-03-08)
```

---

### 2. StandupBurndownTools.java
**路径**: `backend/src/main/java/com/burndown/aiagent/standup/tool/StandupBurndownTools.java`

**功能说明**:
提供给 AI Agent 调用的燃尽图数据查询工具

**核心方法**:
- `getSprintBurndown(GetSprintBurndownRequest request)`: 获取 Sprint 的燃尽图数据

**工作原理**:
1. 查询指定 Sprint 的所有燃尽图数据点
2. 获取最新的数据点（今天或最近的一天）
3. 计算计划剩余工时、实际剩余工时和偏差
4. 统计任务完成情况

**数据说明**:
- **计划剩余工时（Ideal Remaining）**: 理想情况下应该剩余的工时
- **实际剩余工时（Actual Remaining）**: 实际还需要完成的工时
- **偏差（Deviation）**: 实际剩余 - 计划剩余
  - 正值：进度落后
  - 负值：进度超前
  - 零值：进度正常

**AI 调用时机**:
- 用户询问 Sprint 进度
- 用户询问燃尽图情况
- 用户询问剩余工作量

**返回格式**:
```
Sprint: Sprint 1
日期: 2026-03-10
计划剩余工时: 40.0 小时
实际剩余工时: 48.0 小时
偏差: 8.0 小时
已完成任务: 12/20
进行中任务: 5
```

---

### 3. StandupRiskTools.java
**路径**: `backend/src/main/java/com/burndown/aiagent/standup/tool/StandupRiskTools.java`

**功能说明**:
提供给 AI Agent 调用的燃尽图风险评估工具

**核心方法**:
- `evaluateBurndownRisk(EvaluateBurndownRiskRequest request)`: 评估燃尽图偏离风险

**工作原理**:
1. 计算实际剩余工时与计划剩余工时的偏差
2. 计算偏差比例（偏差 / 计划剩余）
3. 根据偏差比例判定风险等级
4. 提供针对性的改进建议

**风险等级判定标准**:

| 风险等级 | 偏差比例 | 说明 | 建议 |
|---------|---------|------|------|
| **LOW** | ≤ 5% | 进度良好，按计划推进 | 继续保持当前节奏 |
| **MEDIUM** | 5% ~ 20% | 存在一定延期风险 | 优先推进高优任务，减少并行工作 |
| **HIGH** | > 20% | 存在严重延期风险 | 立即召开团队会议，重新评估任务优先级和资源分配 |

**计算公式**:
```
偏差 = 实际剩余工时 - 计划剩余工时
偏差比例 = 偏差 / 计划剩余工时
```

**AI 调用时机**:
- 用户询问"有延期风险吗？"
- 用户询问"我们能按时完成吗？"
- AI 在分析燃尽图后自动调用

**返回格式**:
```
风险等级: MEDIUM
偏差比例: 20.0%
偏差工时: 8.0 小时
建议: 存在中等延期风险，建议优先推进高优任务并减少并行工作
```

---

## 🎯 AI Agent Service 类

### StandupAgentService.java
**路径**: `backend/src/main/java/com/burndown/aiagent/standup/service/StandupAgentService.java`

**功能说明**:
Standup Agent 核心服务类，处理 Scrum 站会相关的智能问答请求

**核心功能**:
1. 处理 Scrum 站会相关的智能问答请求
2. 集成 Spring AI 框架，调用大语言模型（LLM）
3. 管理会话上下文，支持多轮对话
4. 提供工具调用能力（Function Calling），让 AI 可以查询任务、燃尽图、风险评估等数据
5. 记录对话历史和性能指标

**核心方法**:

#### 1. `query(StandupQueryRequest request, Long userId, String traceId)`
处理站会问答请求的核心方法

**执行流程**:
1. 记录请求开始时间，增加请求计数指标
2. 创建或获取用户的对话会话（支持上下文记忆）
3. 构建用户提示词（将请求参数填充到模板中）
4. 调用 LLM，并注册可用的工具函数（Function Calling）
5. 解析 LLM 的响应
6. 保存对话记录到数据库
7. 返回响应结果

**注册的工具函数**:
- `getInProgressTasks`: 获取进行中的任务
- `getSprintBurndown`: 获取 Sprint 燃尽图数据
- `evaluateBurndownRisk`: 评估燃尽图风险

**参数说明**:
- `request`: 用户的问答请求，包含问题、项目ID、Sprint ID等信息
- `userId`: 当前用户ID
- `traceId`: 请求追踪ID，用于日志关联和问题排查

**返回值**:
- `StandupQueryResponse`: 站会问答响应，包含 AI 生成的回答、摘要、使用的工具等信息

#### 2. `generateSessionKey(Long userId, Long projectId)`
生成会话唯一标识Key

**格式**: `standup_{userId}_{projectId}_{8位随机UUID}`

**用途**: 标识一个用户在特定项目下的对话会话

#### 3. `getOrCreateSession(String sessionKey, Long userId, Long projectId)`
获取或创建对话会话

**逻辑**:
- 如果会话已存在，则复用（支持上下文记忆）
- 如果会话不存在，则创建新会话并保存到数据库

#### 4. `buildUserPrompt(StandupQueryRequest request)`
构建用户提示词

**功能**: 将请求参数填充到提示词模板中
- `{question}`: 用户的问题
- `{projectId}`: 项目ID
- `{sprintId}`: Sprint ID（可选）
- `{timezone}`: 时区信息

#### 5. `parseResponse(String answer)`
解析 LLM 的响应

**当前实现**: 简单解析，直接返回 AI 的回答文本

**TODO**: 生产环境中可以实现更复杂的解析逻辑
- 提取结构化信息（任务列表、风险等级等）
- 识别 AI 使用了哪些工具
- 提取关键证据和数据来源

#### 6. `saveMessage(...)`
保存对话消息到数据库

**记录内容**:
- 用户问题和 AI 回答
- 使用的工具列表（JSON格式）
- 风险等级
- 请求追踪ID
- 响应延迟（毫秒）

**用途**:
- 对话历史查询
- 性能分析
- 问题排查
- 用户行为分析

---

## 💼 核心 Service 类

### 1. AuthService.java
**路径**: `backend/src/main/java/com/burndown/service/AuthService.java`

**功能说明**: 用户认证服务

**核心功能**:
- 用户注册
- 用户登录
- JWT Token 生成
- 密码加密验证

---

### 2. TaskService.java
**路径**: `backend/src/main/java/com/burndown/service/TaskService.java`

**功能说明**: 任务管理服务

**核心功能**:
- 创建任务
- 更新任务
- 查询任务（按 Sprint、按项目、按ID）
- 更新任务状态

---

### 3. ProjectService.java
**路径**: `backend/src/main/java/com/burndown/service/ProjectService.java`

**功能说明**: 项目管理服务

**核心功能**:
- 创建项目
- 查询项目（全部、按ID、按所有者）
- 项目权限管理

---

### 4. SprintService.java
**路径**: `backend/src/main/java/com/burndown/service/SprintService.java`

**功能说明**: Sprint 管理服务

**核心功能**:
- 创建 Sprint
- 查询 Sprint（按项目、按ID）
- 启动 Sprint
- 完成 Sprint

---

### 5. BurndownService.java
**路径**: `backend/src/main/java/com/burndown/service/BurndownService.java`

**功能说明**: 燃尽图服务

**核心功能**:
- 计算燃尽图数据
- 查询燃尽图数据点
- 生成每日快照

---

### 6. WorkLogService.java
**路径**: `backend/src/main/java/com/burndown/service/WorkLogService.java`

**功能说明**: 工作日志服务

**核心功能**:
- 记录工作日志
- 查询工作日志（按任务、按用户）
- 工时统计

---

### 7. UserService.java
**路径**: `backend/src/main/java/com/burndown/service/UserService.java`

**功能说明**: 用户管理服务

**核心功能**:
- 用户信息查询
- 用户信息更新
- 用户列表查询

---

### 8. RoleService.java
**路径**: `backend/src/main/java/com/burndown/service/RoleService.java`

**功能说明**: 角色管理服务（RBAC）

**核心功能**:
- 创建角色
- 更新角色
- 删除角色
- 查询角色
- 角色权限管理

---

### 9. PermissionService.java
**路径**: `backend/src/main/java/com/burndown/service/PermissionService.java`

**功能说明**: 权限管理服务（RBAC）

**核心功能**:
- 查询所有权限
- 查询角色权限
- 权限验证

---

### 10. UserRoleService.java
**路径**: `backend/src/main/java/com/burndown/service/UserRoleService.java`

**功能说明**: 用户角色关联服务（RBAC）

**核心功能**:
- 分配角色给用户
- 移除用户角色
- 查询用户角色
- 查询角色下的用户

---

## 🤖 AI 相关 Service 类

### 1. TaskAiService.java
**路径**: `backend/src/main/java/com/burndown/service/TaskAiService.java`

**功能说明**: AI 任务描述生成服务（RAG）

**核心功能**:
1. 使用 RAG（检索增强生成）生成任务描述
2. 基于向量相似度检索相似任务
3. 构建上下文提示词
4. 调用 LLM 生成任务描述
5. 记录生成日志和用户反馈

**工作流程**:
```
用户输入任务标题
    ↓
检索相似任务（向量搜索）
    ↓
构建提示词（项目上下文 + 相似任务）
    ↓
调用 LLM 生成描述
    ↓
返回生成结果
    ↓
记录日志
```

**相似度算法**:
- 标题关键词匹配：55%
- 任务类型匹配：25%
- 优先级匹配：15%
- 故事点匹配：5%

**核心方法**:
- `generateDescription()`: 生成任务描述
- `findSimilarTasks()`: 查找相似任务
- `buildPrompt()`: 构建提示词
- `submitFeedback()`: 提交用户反馈

---

### 2. AiClientService.java
**路径**: `backend/src/main/java/com/burndown/service/AiClientService.java`

**功能说明**: AI 客户端服务，封装对外部 LLM API 的调用

**核心功能**:
- HTTP 客户端封装
- 请求/响应处理
- 错误处理和重试
- 超时控制

**支持的 API 格式**:
- OpenAI 兼容格式
- 自定义 LLM API

---

### 3. EmbeddingService.java
**路径**: `backend/src/main/java/com/burndown/service/EmbeddingService.java`

**功能说明**: 向量嵌入服务，用于 RAG 功能

**核心功能**:
1. 生成文本的向量嵌入（Embedding）
2. 调用外部 Embedding API
3. 向量存储到 PostgreSQL（pgvector）
4. 向量相似度搜索

**工作原理**:
```
任务文本
    ↓
调用 Embedding API
    ↓
生成向量（如 1536 维）
    ↓
存储到 pgvector
    ↓
支持相似度搜索
```

---

### 4. TaskEmbeddingBatchService.java
**路径**: `backend/src/main/java/com/burndown/service/TaskEmbeddingBatchService.java`

**功能说明**: 任务向量批量生成服务

**核心功能**:
- 批量为历史任务生成向量嵌入
- 定时任务调度
- 增量更新
- 错误处理和重试

**使用场景**:
- 系统初始化时批量生成向量
- 定期更新任务向量
- 数据迁移

---

### 5. RateLimitService.java
**路径**: `backend/src/main/java/com/burndown/service/RateLimitService.java`

**功能说明**: 速率限制服务

**核心功能**:
- 基于 Redis 的速率限制
- 滑动窗口算法
- 用户级别限流
- 重置时间计算

**使用场景**:
- AI 生成接口限流
- 防止 API 滥用
- 成本控制

**配置示例**:
```yaml
ai:
  rate-limit:
    max-requests-per-user: 10  # 每个用户最多请求次数
    window-minutes: 60          # 时间窗口（分钟）
```

---

## 🔄 Spring AI Function Calling 工作原理

### 什么是 Function Calling？

Function Calling 是 Spring AI 框架提供的一种机制，允许 LLM 在对话过程中自动调用预定义的工具函数来获取数据。

### 工作流程

```
用户提问："我今天有哪些任务？"
    ↓
发送到 LLM（带工具函数列表）
    ↓
LLM 分析：需要调用 getInProgressTasks 工具
    ↓
LLM 生成工具调用请求（JSON格式）
    ↓
Spring AI 框架拦截并执行工具函数
    ↓
工具函数返回数据
    ↓
数据返回给 LLM
    ↓
LLM 整合数据生成自然语言回答
    ↓
返回给用户："您有 3 个任务正在进行中..."
```

### 如何定义工具函数？

1. **使用 @Component 注解**，让 Spring 管理工具类
2. **方法上使用 @Description 注解**，描述工具的功能（AI 会根据这个描述决定是否调用）
3. **参数使用 record 类型**，配合 @JsonProperty 和 @JsonPropertyDescription 注解
4. **返回字符串格式的结果**，AI 会将结果整合到最终回答中

### 示例代码

```java
@Component
public class StandupTaskTools {

    @Description("获取用户当前进行中的任务列表")
    public String getInProgressTasks(GetInProgressTasksRequest request) {
        // 查询任务
        List<Task> tasks = taskRepository.findByProjectId(request.projectId())
            .stream()
            .filter(task -> task.getStatus() == Task.TaskStatus.IN_PROGRESS)
            .collect(Collectors.toList());

        // 返回格式化的字符串
        return "找到 " + tasks.size() + " 个进行中的任务：\n" + ...;
    }

    public record GetInProgressTasksRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("项目 ID")
        Long projectId
    ) {}
}
```

### 注册工具函数

在 Service 中注册工具函数：

```java
ChatResponse response = chatClient.prompt()
    .system(StandupPromptTemplate.SYSTEM_PROMPT)
    .user(userPrompt)
    .functions("getInProgressTasks", "getSprintBurndown", "evaluateBurndownRisk")
    .call()
    .chatResponse();
```

---

## 📊 性能监控

### Metrics 指标

所有 AI 相关服务都集成了 Micrometer 性能监控：

**计数器（Counter）**:
- `ai.generation.requests.total`: 总请求数
- `ai.generation.success.total`: 成功次数
- `ai.generation.failure.total`: 失败次数
- `ai.generation.fallback.total`: 降级次数

**计时器（Timer）**:
- `ai.generation.duration`: 生成耗时

**查看方式**:
```bash
curl http://localhost:8080/api/v1/actuator/prometheus | grep ai_generation
```

---

## 🔐 安全考虑

### 1. 认证授权
- 所有 AI 接口都需要 JWT Token 认证
- 基于 RBAC 的权限控制

### 2. 速率限制
- 基于 Redis 的用户级别限流
- 防止 API 滥用和成本失控

### 3. 数据隔离
- 用户只能查询自己的任务和项目
- 会话隔离，防止数据泄露

### 4. 输入验证
- 使用 Jakarta Validation 进行参数校验
- 防止 SQL 注入和 XSS 攻击

---

## 🚀 使用建议

### 1. 开发环境配置

```yaml
ai:
  enabled: true
  base-url: "https://api.openai.com/v1"
  api-key: "your-api-key"
  chat-model: "gpt-4"
  timeout: 30s
  max-similar-tasks: 5
  rate-limit:
    max-requests-per-user: 10
    window-minutes: 60
```

### 2. 生产环境优化

- 启用 Redis 缓存
- 配置合理的超时时间
- 设置严格的速率限制
- 监控 API 调用成本
- 定期清理历史日志

### 3. 调试技巧

- 查看后端日志了解工具调用情况
- 使用 traceId 追踪请求链路
- 查看 Prometheus 指标分析性能
- 测试不同的问题表述方式

---

**版本**: 1.0.0
**更新日期**: 2026-03-10
**维护者**: Burndown Development Team
