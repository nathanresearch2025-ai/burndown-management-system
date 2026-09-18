# MCP Scrum Standup Server 需求文档

**版本：** v1.0
**日期：** 2026-03-25
**状态：** 草稿

---

## 1. 背景与动机

### 1.1 现状痛点

当前 Burndown Management System 已具备完整的 Scrum 数据（项目、Sprint、任务、燃尽图、工作日志），以及 AI Standup Agent。但这些能力仅通过 REST API 暴露，AI 工具（如 Claude Desktop、Cursor）无法直接感知项目上下文，每次交互都需用户手动复制粘贴数据。

### 1.2 MCP 解决什么问题

[Model Context Protocol (MCP)](https://modelcontextprotocol.io) 是 Anthropic 提出的开放标准，允许 AI 模型通过统一协议访问外部工具和数据源。

通过实现一个 **MCP Scrum Standup Server**，开发者可以在 Claude Desktop / Cursor 中直接问：

- "我们 Sprint-12 今天有哪些任务在进行中？"
- "当前燃尽图是否健康，有哪些风险任务？"
- "帮我生成今天的站会摘要"

无需离开 IDE，无需手动调用 REST API。

---

## 2. 产品目标

| 目标 | 衡量指标 |
|------|----------|
| 让 AI 工具直接访问 Scrum 实时数据 | Claude Desktop 可无缝调用所有 MCP Tool |
| 减少开发者手动查数据时间 | 站会准备时间从 5 分钟降至 < 30 秒 |
| 复用现有后端能力，零重复开发 | MCP Server 仅做协议适配，不包含业务逻辑 |

---

## 3. 使用场景（Use Cases）

### UC-01：每日站会助手

**用户：** Scrum 团队成员（开发者）
**触发：** 每天站会前，在 Claude Desktop 中提问
**流程：**
1. 用户问："Sprint 12 今天进行中的任务是什么？"
2. Claude 调用 MCP Tool `get_active_sprint_tasks`
3. MCP Server 请求后端 `GET /tasks/sprint/{id}` 并过滤状态
4. Claude 以自然语言返回结构化摘要

**价值：** 站会主持人 30 秒内获取全团队进展，无需打开 Web UI。


### UC-02：燃尽图风险预警

**用户：** Scrum Master
**触发：** Sprint 中期，在 Cursor 中询问项目健康度
**流程：**
1. 用户问："当前 Sprint 燃尽图正常吗，有风险吗？"
2. Claude 调用 MCP Tool `get_burndown_data` + `get_sprint_completion_probability`
3. MCP Server 聚合燃尽数据和预测概率
4. Claude 分析趋势并给出风险等级和建议

**价值：** Scrum Master 无需切换工具，直接在 IDE 中获得数据驱动的风险判断。

### UC-03：任务智能创建

**用户：** 开发者
**触发：** 编写代码过程中发现需要拆分新任务
**流程：**
1. 用户说："帮我为当前登录功能创建一个安全审查任务，HIGH 优先级，3 故事点"
2. Claude 调用 MCP Tool `ai_generate_task_description` 生成任务描述
3. 再调用 MCP Tool `create_task` 写入系统
4. 返回新建任务的 taskKey（如 `AUTH-45`）

**价值：** 不离开 IDE 即可完成任务创建，描述由 AI RAG 自动生成。

---

## 4. MCP Server 设计

### 4.1 架构图

```
┌─────────────────────────────────────┐
│         AI Client Layer             │
│  Claude Desktop / Cursor / IDE      │
└──────────────┬──────────────────────┘
               │ MCP Protocol (stdio / HTTP+SSE)
┌──────────────▼──────────────────────┐
│       MCP Scrum Standup Server      │
│  (Node.js / Python / Java)          │
│  - Tool 定义 & Schema 注册           │
│  - 参数校验 & 错误处理               │
│  - JWT Token 注入                   │
└──────────────┬──────────────────────┘
               │ REST API (HTTP)
┌──────────────▼──────────────────────┐
│    Burndown Management Backend      │
│    Spring Boot 3.2 / Java 21        │
│    PostgreSQL + Redis + pgvector    │
└─────────────────────────────────────┘
```

### 4.2 传输协议

- **开发阶段：** `stdio`（本地进程通信，Claude Desktop 直接启动）
- **生产阶段：** `HTTP + SSE`（支持远程访问和多用户）


### 4.3 MCP Tools 清单

| Tool Name | 描述 | 对应后端 API |
|-----------|------|-------------|
| `get_projects` | 获取所有项目列表 | `GET /projects` |
| `get_active_sprint` | 获取项目当前活跃 Sprint | `GET /sprints/project/{id}` 过滤 ACTIVE |
| `get_active_sprint_tasks` | 获取 Sprint 内所有任务，可按状态过滤 | `GET /tasks/sprint/{id}` |
| `get_burndown_data` | 获取 Sprint 燃尽图数据点 | `GET /burndown/sprints/{id}` |
| `get_sprint_completion_probability` | 获取 Sprint 完成概率预测 | `GET /sprints/{id}/completion-probability` |
| `get_my_worklogs` | 获取当前用户工作日志 | `GET /worklogs/my-worklogs` |
| `create_task` | 在指定 Sprint 中创建新任务 | `POST /tasks` |
| `update_task_status` | 更新任务状态 | `PATCH /tasks/{id}/status` |
| `ai_generate_task_description` | AI 生成任务描述（RAG） | `POST /tasks/ai/generate-description` |
| `search_similar_tasks` | 向量语义搜索相似任务 | `POST /similarity/search` |

### 4.4 MCP Resources（只读数据源）

| Resource URI | 描述 |
|-------------|------|
| `scrum://projects` | 所有项目列表 |
| `scrum://sprint/{sprintId}/board` | Sprint 看板（按状态分组的任务） |
| `scrum://sprint/{sprintId}/burndown` | 燃尽图数据 |
| `scrum://user/me/tasks` | 当前用户分配的任务 |

### 4.5 MCP Prompts（预置提示模板）

| Prompt Name | 描述 | 参数 |
|------------|------|------|
| `daily_standup` | 生成每日站会报告 | `sprintId` |
| `sprint_health_check` | Sprint 健康度分析 | `sprintId` |
| `task_breakdown` | 将用户描述拆解为子任务 | `description`, `projectId` |

---

## 5. 技术实现方案

### 5.1 推荐技术栈

**方案 A（推荐）：TypeScript + @modelcontextprotocol/sdk**

```
mcp-scrum-server/
├── src/
│   ├── index.ts          # MCP Server 入口，注册 tools/resources/prompts
│   ├── tools/
│   │   ├── sprint.ts     # get_active_sprint, get_active_sprint_tasks
│   │   ├── burndown.ts   # get_burndown_data, get_sprint_completion_probability
│   │   ├── task.ts       # create_task, update_task_status, ai_generate_task_description
│   │   └── search.ts     # search_similar_tasks
│   ├── resources/
│   │   └── board.ts      # Sprint 看板 Resource
│   ├── prompts/
│   │   └── standup.ts    # daily_standup Prompt 模板
│   └── client/
│       └── api.ts        # 封装 Axios 调用后端 REST API
├── package.json
└── mcp-config.json       # Claude Desktop 配置示例
```

**方案 B：Python + mcp SDK**（适合与现有 LangChain 服务集成）


### 5.2 认证方案

MCP Server 以服务账号身份与后端通信，使用固定 JWT Token（或 Basic Auth 换取 Token）：

```typescript
// mcp-config.json 中配置
{
  "mcpServers": {
    "scrum-standup": {
      "command": "node",
      "args": ["dist/index.js"],
      "env": {
        "BACKEND_URL": "http://localhost:8080/api/v1",
        "BACKEND_USERNAME": "mcp-agent",
        "BACKEND_PASSWORD": "your-password"
      }
    }
  }
}
```

MCP Server 启动时调用 `POST /auth/login` 获取 JWT，后续所有请求携带 `Authorization: Bearer <token>`。

### 5.3 关键 Tool 实现示例

```typescript
// get_active_sprint_tasks Tool 定义
server.tool(
  "get_active_sprint_tasks",
  "获取当前活跃 Sprint 的任务列表，可按状态过滤",
  {
    projectId: z.number().describe("项目 ID"),
    status: z.enum(["TODO", "IN_PROGRESS", "IN_REVIEW", "DONE", "BLOCKED"])
              .optional().describe("任务状态过滤（不填则返回全部）")
  },
  async ({ projectId, status }) => {
    const sprint = await api.getActiveSprint(projectId);
    if (!sprint) return { content: [{ type: "text", text: "当前无活跃 Sprint" }] };
    const tasks = await api.getSprintTasks(sprint.id, status);
    return {
      content: [{ type: "text", text: JSON.stringify(tasks, null, 2) }]
    };
  }
);
```

---

## 6. 非功能需求

| 类别 | 要求 |
|------|------|
| 性能 | 单次 Tool 调用响应 < 2 秒（复用后端缓存） |
| 安全 | JWT Token 不写入日志；env var 注入密钥 |
| 兼容性 | 支持 Claude Desktop（macOS/Windows）、Cursor、任何 MCP 兼容客户端 |
| 可观测性 | 每次 Tool 调用记录请求参数和响应状态到本地日志文件 |
| 错误处理 | 后端不可达时返回友好错误描述，不暴露堆栈 |

---

## 7. 实施计划

| 阶段 | 任务 | 产出 |
|------|------|------|
| Phase 1（基础） | 搭建 MCP Server 框架，实现 `get_active_sprint_tasks`、`get_burndown_data` | 可在 Claude Desktop 中查询 Sprint 数据 |
| Phase 2（增强） | 实现全部 10 个 Tool，添加 Resources 和 Prompts | 完整站会助手能力 |
| Phase 3（生产） | 切换 HTTP+SSE 传输，添加日志和监控 | 支持团队多人共用 |

---

## 8. 与现有系统的关系

```
现有 AI 能力                    MCP 新增
─────────────────────────────────────────
Standup Agent (REST)    →   MCP Tool 封装，IDE 直接调用
RAG 任务生成 (REST)     →   MCP Tool `ai_generate_task_description`
向量相似搜索 (REST)     →   MCP Tool `search_similar_tasks`
燃尽图计算 (REST)       →   MCP Resource `scrum://sprint/{id}/burndown`
```

MCP Server 是现有后端的**零侵入适配层**，不修改任何后端代码。

---

## 9. 验收标准

- [ ] Claude Desktop 配置 MCP Server 后，可用自然语言查询任意项目的活跃 Sprint 任务
- [ ] 可通过对话触发 AI 任务描述生成并自动创建任务
- [ ] 燃尽图数据可在对话中以文字形式分析趋势
- [ ] 所有 Tool 调用有日志记录
- [ ] 提供 `mcp-config.json` 示例供团队一键接入

---

*文档由 Claude Code 生成 | 持续监控，一步步显示*