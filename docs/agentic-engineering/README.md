# Agentic Engineering：基于现有 Backend 的 LangChain 全链路示例

本文结合当前后端工程（`backend`）的 AI Agent 能力，说明 LangChain 如何独立跑通「需求 → 规划 → 工具调用 → 记忆 → 多 Agent 协作 → 部署 → 监控」的全链路，并给出一个简单需求场景。

## 1. 结合现有 Backend 的能力映射

项目中已经具备一条典型 Agent 链路的落地形态（站会 Agent），涵盖：

- **需求理解与规划**：`StandupAgentService` 负责将用户问题转换为可执行的提示词，并决定调用哪些工具函数。
- **工具调用**：`StandupTaskTools`、`StandupBurndownTools`、`StandupRiskTools` 通过 Function Calling 提供数据能力。
- **记忆（上下文）**：`AgentChatSession` 与 `AgentChatMessage` 记录会话与消息。
- **多 Agent 协作（可扩展）**：可在现有工具基础上拆分角色，如 Data/Analysis/Writer Agent。
- **部署**：Spring Boot 服务已对外提供 API（`StandupAgentController`）。
- **监控**：已有 `prometheus.yml` 与 `alert_rules.yml`，并通过 `StandupAgentMetrics` 采集指标。

现有关键代码参考：

```58:149:D:/java/claude/projects/2/backend/src/main/java/com/burndown/aiagent/standup/service/StandupAgentService.java
    @Transactional
    public StandupQueryResponse query(StandupQueryRequest request, Long userId, String traceId) {
        long startTime = System.currentTimeMillis();
        // 增加请求总数指标
        metrics.getRequestsTotal().increment();

        try {
            // 步骤1：创建或获取会话
            String sessionKey = generateSessionKey(userId, request.getProjectId());
            AgentChatSession session = getOrCreateSession(sessionKey, userId, request.getProjectId());

            // 步骤2：构建用户提示词
            String userPrompt = buildUserPrompt(request);

            // 步骤3：调用 LLM（大语言模型）
            ChatClient chatClient = chatClientBuilder.build();

            ChatResponse response = metrics.getDurationTimer().record(() ->
                    chatClient.prompt()
                            .system(StandupPromptTemplate.SYSTEM_PROMPT)
                            .user(userPrompt)
                            .functions("getInProgressTasks", "getSprintBurndown", "evaluateBurndownRisk")
                            .call()
                            .chatResponse()
            );

            String answer = response.getResult().getOutput().getContent();
            StandupQueryResponse queryResponse = parseResponse(answer);

            long latency = System.currentTimeMillis() - startTime;
            saveMessage(session.getId(), request.getQuestion(), answer, queryResponse, traceId, (int) latency);

            return queryResponse;

        } catch (Exception e) {
            log.error("Error processing standup query: {}", e.getMessage(), e);
            metrics.getFallbackTotal().increment();
            throw new RuntimeException("Failed to process query: " + e.getMessage(), e);
        }
    }
```

工具调用示例：

```33:121:D:/java/claude/projects/2/backend/src/main/java/com/burndown/aiagent/standup/tool/StandupTaskTools.java
    @Description("获取用户当前进行中的任务列表")
    public String getInProgressTasks(GetInProgressTasksRequest request) {
        log.info("Tool called: getInProgressTasks - projectId: {}, userId: {}",
                request.projectId(), request.userId());

        try {
            List<Task> tasks = taskRepository.findByProjectId(request.projectId()).stream()
                    .filter(task -> task.getAssigneeId() != null &&
                                  task.getAssigneeId().equals(request.userId()) &&
                                  task.getStatus() == Task.TaskStatus.IN_PROGRESS)
                    .collect(Collectors.toList());

            if (tasks.isEmpty()) {
                return "当前没有进行中的任务";
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("找到 %d 个进行中的任务：\n", tasks.size()));

            for (Task task : tasks) {
                result.append(String.format("- %s: %s (优先级: %s, 故事点: %s, 更新时间: %s)\n",
                        task.getTaskKey(),
                        task.getTitle(),
                        task.getPriority(),
                        task.getStoryPoints(),
                        task.getUpdatedAt()));
            }

            return result.toString();

        } catch (Exception e) {
            log.error("Error getting in-progress tasks: {}", e.getMessage(), e);
            return "获取任务失败: " + e.getMessage();
        }
    }
```

监控配置示例：

```1:20:D:/java/claude/projects/2/backend/monitoring/prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - /etc/prometheus/alert_rules.yml

scrape_configs:
  - job_name: burndown-backend
    metrics_path: /api/v1/actuator/prometheus
    static_configs:
      - targets:
          - host.docker.internal:8080
        labels:
          application: burndown-management
          environment: local
```

---

## 2. 简单需求场景（结合真实业务数据字段）

**场景：** 运营同学输入 “我想要最近两个 Sprint 的任务趋势与燃尽风险摘要”。

下面用后端已有的数据字段与工具能力，**一步步从用户提示词到最终结果**。

### Step 1：用户输入（前端/接口）

用户在前端输入提示词，最终进入 `StandupAgentController`：

- `question`: 我想要最近两个 Sprint 的任务趋势与燃尽风险摘要
- `projectId`: 1001
- `sprintId`: 302（可选，用于定位当前 Sprint）
- `userId`: 21
- `timezone`: Asia/Shanghai

### Step 2：需求理解与规划（StandupAgentService）

系统判断要输出“趋势 + 风险摘要”，需要以下数据：

1. **任务趋势**（任务数量、完成情况）
2. **燃尽图风险**（燃尽曲线偏离情况）

因此规划为 3 个步骤：

- 调用 `getInProgressTasks` 获取当前 Sprint 任务概况
- 调用 `getSprintBurndown` 获取燃尽图数据
- 调用 `evaluateBurndownRisk` 评估风险等级

### Step 3：工具调用（真实数据字段）

**工具 1：getInProgressTasks**
- 读取 `Task` 表字段：`taskKey`, `title`, `priority`, `storyPoints`, `status`, `updatedAt`
- 返回示例（格式为工具返回字符串）：
  - 找到 3 个进行中的任务：
  - `TASK-214: 登录页性能优化 (优先级: HIGH, 故事点: 5, 更新时间: 2026-03-10 11:42)`
  - `TASK-219: 用户中心加载优化 (优先级: MEDIUM, 故事点: 3, 更新时间: 2026-03-11 09:15)`
  - `TASK-223: 报表导出稳定性 (优先级: HIGH, 故事点: 8, 更新时间: 2026-03-11 18:27)`

**工具 2：getSprintBurndown**
- 读取 `BurndownPoint` 表字段：`remainingPoints`, `date`
- 返回示例：
  - `2026-03-03: 42`
  - `2026-03-06: 39`
  - `2026-03-09: 37`
  - `2026-03-11: 36`

**工具 3：evaluateBurndownRisk**
- 基于燃尽曲线偏离度评估风险
- 返回示例：`riskLevel = HIGH, deviation = +18%`

### Step 4：记忆（会话存储）

系统将本轮会话写入：

- `AgentChatSession`：会话维度信息
- `AgentChatMessage`：本轮问答内容、工具调用结果、风险等级、延迟等

后续用户继续追问“给我具体影响最大的任务”，系统可以直接复用上下文。

### Step 5：生成最终摘要（响应内容）

模型将工具返回的数据整合成自然语言摘要，例如：

- 近两次 Sprint 中进行中的任务集中在性能优化与稳定性方向，Story Points 总计 16，任务密度偏高。
- 燃尽曲线在中期偏离 18%，当前剩余点数 36，风险等级为 HIGH。
- 建议优先推进 `TASK-214` 与 `TASK-223` 以降低风险。

### Step 6：最终返回给前端

API 返回结构化结果（`StandupQueryResponse`），包含：

- `answer`：上述摘要
- `summary`：风险等级、关键结论
- `toolsUsed`：工具列表
- `evidence`：数据来源片段

以上实现完全复用现有 Spring Boot 数据模型与工具体系，不需要重写业务逻辑。

---

## 3. 基于 LangChain 的后端链路示例（简化）

> 该示例仅展示“需求 → 规划 → 工具调用 → 记忆 → 输出”的链路结构，方便对照现有 Spring AI 的实现。

```python
from langchain.agents import AgentExecutor, Tool
from langchain_openai import ChatOpenAI
from langchain.memory import ConversationBufferMemory

# 1) 工具定义（映射现有后端能力）

def get_sprint_tasks(query: str) -> str:
    return "Sprint A: 36 tasks, Sprint B: 41 tasks"

def get_burndown_data(query: str) -> str:
    return "Sprint A risk: LOW, Sprint B risk: HIGH"

TOOLS = [
    Tool(name="GetSprintTasks", func=get_sprint_tasks, description="查询 Sprint 任务统计"),
    Tool(name="GetBurndownData", func=get_burndown_data, description="获取燃尽图风险信息")
]

# 2) 记忆
memory = ConversationBufferMemory(memory_key="chat_history", return_messages=True)

# 3) 规划+执行
llm = ChatOpenAI(model="gpt-4o-mini")
agent = AgentExecutor.from_agent_and_tools(
    agent=llm,
    tools=TOOLS,
    memory=memory,
    verbose=True
)

result = agent.invoke({"input": "分析最近两个 Sprint 的趋势和风险摘要"})
print(result)
```

---

## 4. 部署与监控要点（结合当前工程）

- **部署**：继续沿用 Spring Boot API（如 `StandupAgentController`），供前端或业务系统调用。
- **监控**：保持 `Actuator + Prometheus` 采集，并在 `alert_rules.yml` 中设置异常告警。

---

## 5. 小结

当前后端已经具备 Agentic 工程的核心骨架：
- 有工具能力、有记忆、有指标、有 API。
- 只需在现有服务基础上扩展“规划 / 多 Agent 协作 / 更强的工具编排策略”，即可形成完整的 LangChain 级别链路。

如果需要，我可以基于你现有的 `StandupAgentService` 再补一份“多 Agent 分工 + 路由策略”的后端增强方案。