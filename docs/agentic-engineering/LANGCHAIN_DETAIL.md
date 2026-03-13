# LangChain 具体做了什么（技术细节版）

本文聚焦“LangChain 在系统里到底做了什么”，并结合你当前 Spring Boot 体系说明其技术细节与边界。

---

## 1. LangChain 的核心职责（不是业务层）
LangChain 的核心定位是 **智能编排层**，负责：

1. **需求理解**：解析用户输入，抽取目标、约束、所需数据
2. **规划（Planning）**：把目标拆成可执行的步骤
3. **工具调用（Tool Calling）**：选择并调用外部工具（API/DB/服务）
4. **多 Agent 协作**：多角色分工、路由、合并结论
5. **记忆（Memory）**：在多轮对话中维护上下文
6. **可观测性**：记录链路、调用、耗时、失败原因（配合 LangSmith）

**LangChain 不负责：**
- 业务数据正确性
- 权限校验
- 事务处理
- 核心业务接口

这些仍归 Spring Boot 负责。

---

## 2. 具体技术细节

### 2.1 需求理解（Prompt + Parser）
LangChain 基于 Prompt 模板解析输入，得到：
- 用户意图（intent）
- 关键参数（projectId、时间范围、指标）
- 约束条件（最近两次 Sprint）

技术点：
- `PromptTemplate` 结构化输入
- 输出结构化字段时可配合 `OutputParser`


### 2.2 规划（Plan）
当目标复杂时，LangChain 会先执行规划，再逐步调用工具。

常见规划方式：
- **LLM Planner**：由模型生成步骤
- **Chain of Thought / Plan-and-Execute**

示例规划：
1. 拉取 Sprint 任务统计
2. 拉取燃尽图数据
3. 评估风险
4. 汇总为摘要


### 2.3 工具调用（Tool / Function Calling）
LangChain 将系统能力抽象成工具：

- 工具定义：`Tool(name, func, description)`
- LLM 根据描述判断要不要调用
- LangChain 负责传参、执行、返回结果给模型

在你系统里，工具通常是 **Spring Boot API**：

- `/api/task/in-progress`
- `/api/sprint/burndown`
- `/api/sprint/risk`

工具返回的是 **结构化或半结构化数据**，LangChain 负责融合成最终回答。


### 2.4 记忆（Memory）
LangChain 的记忆通常实现为：

- **ConversationBufferMemory**（简单缓存）
- **VectorStoreMemory**（向量化长期记忆）

你当前 Spring Boot 已有 `AgentChatSession` + `AgentChatMessage`，可以等价作为 LangChain 的存储源。


### 2.5 多 Agent 协作
LangChain 支持多 Agent 分工并行或流水线协作：

- Planner Agent：拆分任务
- Data Agent：查询数据
- Analyst Agent：分析趋势与风险
- Writer Agent：生成最终摘要

典型实现：
- `MultiAgentExecutor` / `Crew` 模式
- Tool 路由或消息路由


### 2.6 可观测性（LangSmith）
LangChain 可集成 LangSmith 监控：

- 每次调用的链路记录
- 工具调用日志
- 失败原因、耗时、结果

你系统里已有 Prometheus/Actuator，二者可并行使用。

---

## 3. 与 Spring Boot 的组合方式

### 模式 A：LangChain 独立服务
- Spring Boot 只负责业务能力
- LangChain 服务负责智能编排
- LangChain 调用 Spring Boot API 工具

链路：
前端 → Spring Boot → LangChain → Spring Boot 工具 API → LangChain → Spring Boot → 前端


### 模式 B：不引入 LangChain（用 Spring AI）
- Spring Boot 内部直接做编排与工具调用
- 功能上接近 LangChain，但少了其生态与可观测能力

你当前项目属于这种形态（StandupAgentService）。

---

## 4. 最小可用落地方案（推荐）

若要在现有系统上最小成本落地 LangChain：

1. 保留 Spring Boot 业务与数据层
2. 新增 LangChain 服务（Python）
3. 把现有工具能力暴露成 API
4. LangChain 负责规划 + 调用工具 + 汇总
5. Spring Boot 只负责接入与返回结果

---

## 5. 小结

LangChain 的价值在于 **智能编排** 与 **多 Agent 协作**。它不替代业务系统，而是站在业务系统之上，决定“调用谁、调用几次、如何组合结果”。

你当前 Spring Boot 系统已经具备工具、记忆、监控能力，若引入 LangChain，主要提升的是：

- 复杂任务的自动规划能力
- 多 Agent 协作与路由能力
- LangSmith 级别的调用链追踪

