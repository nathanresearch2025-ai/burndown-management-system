# MCP Scrum Standup Server — 安装与启动指南

**适用客户端：** VS Code + Cline 插件
**最后更新：** 2026-03-25

---

## 目录

1. [前置条件](#1-前置条件)
2. [安装 MCP Server](#2-安装-mcp-server)
3. [在 VS Code Cline 中配置 MCP](#3-在-vs-code-cline-中配置-mcp)
4. [启动后端服务](#4-启动后端服务)
5. [验证连接](#5-验证连接)
6. [使用示例](#6-使用示例)
7. [故障排查](#7-故障排查)

---

## 1. 前置条件

| 依赖 | 版本要求 | 说明 |
|------|----------|------|
| Node.js | >= 18.0 | 运行 MCP Server |
| npm | >= 9.0 | 安装依赖 |
| VS Code | >= 1.85 | 运行 Cline 插件 |
| Cline 插件 | >= 3.0 | VS Code 扩展市场安装，支持 MCP |
| 后端服务 | 运行中 | Spring Boot 后端需可访问 |

检查 Node.js 版本：
```bash
node --version   # 需要 v18+
npm --version
```

---

## 2. 安装 MCP Server

### 2.1 进入项目目录

```bash
cd D:/java/claude/projects/2/mcp-server
```

### 2.2 安装依赖

```bash
npm install
```

### 2.3 编译 TypeScript

```bash
npm run build
```

编译成功后，`dist/` 目录下会生成以下文件：
```
dist/
├── index.js              # 主入口（Cline 启动此文件）
├── client/
│   └── api.js
├── tools/
│   ├── sprint.js
│   ├── burndown.js
│   ├── task.js
│   └── search.js
├── resources/
│   └── board.js
└── prompts/
    └── standup.js
```

### 2.4 记录绝对路径（后续配置需要）

```bash
# Windows
echo %cd%\dist\index.js
# 输出示例：D:\java\claude\projects\2\mcp-server\dist\index.js
```

---

## 3. 在 VS Code Cline 中配置 MCP

### 3.1 安装 Cline 插件

1. 打开 VS Code
2. 按 `Ctrl+Shift+X` 打开扩展市场
3. 搜索 **Cline**，安装（作者：saoudrizwan）
4. 安装完成后在左侧活动栏可见 Cline 图标

### 3.2 配置 Cline 使用的 AI 模型

首次使用 Cline 需配置 API Provider：
1. 点击左侧 Cline 图标打开面板
2. 点击右上角设置（齿轮图标）
3. 选择 API Provider（如 Anthropic、OpenAI 等）并填入 API Key

### 3.3 打开 Cline MCP 配置文件

Cline 的 MCP 配置文件位于：

| 系统 | 路径 |
|------|------|
| Windows | `%APPDATA%\Code\User\globalStorage\saoudrizwan.claude-dev\settings\cline_mcp_settings.json` |
| macOS | `~/Library/Application Support/Code/User/globalStorage/saoudrizwan.claude-dev/settings/cline_mcp_settings.json` |

也可以通过 Cline 界面进入：
```
Cline 面板 → 点击 MCP Servers 图标（插头图标）→ Edit MCP Settings
```

### 3.4 添加 MCP Server 配置

编辑 `cline_mcp_settings.json`，添加以下内容（替换路径和密码）：

```json
{
  "mcpServers": {
    "scrum-standup": {
      "command": "node",
      "args": [
        "D:/java/claude/projects/2/mcp-server/dist/index.js"
      ],
      "env": {
        "BACKEND_URL": "http://localhost:8080/api/v1",
        "BACKEND_USERNAME": "admin",
        "BACKEND_PASSWORD": "your-password",
        "LOG_LEVEL": "info"
      },
      "disabled": false,
      "autoApprove": []
    }
  }
}
```

> **注意：** Cline 配置中路径使用正斜杠 `/` 即可，无需双写反斜杠。

### 3.5 验证 MCP Server 已加载

保存配置文件后，Cline 会**自动热重载**（无需重启 VS Code）。

在 Cline 面板点击插头图标（MCP Servers），应看到 `scrum-standup` 状态为 **Connected**，展开后可见所有已注册工具：
- `get_projects`
- `get_active_sprint`
- `get_active_sprint_tasks`
- `get_burndown_data`
- `get_sprint_completion_probability`
- `trigger_burndown_snapshot`
- `create_task`
- `update_task_status`
- `ai_generate_task_description`
- `get_my_worklogs`
- `search_similar_tasks`

---

## 4. 启动后端服务

MCP Server 需要后端 Spring Boot 服务运行。

### 4.1 本地开发环境

```bash
cd D:/java/claude/projects/2/backend
mvn spring-boot:run
```

后端启动后验证：
```bash
curl http://localhost:8080/api/v1/actuator/health
# 预期返回：{"status":"UP"}
```

### 4.2 Kubernetes 环境

如果后端已部署在 K8s，修改 `BACKEND_URL` 为对应 NodePort 地址：

```json
"BACKEND_URL": "http://<node-ip>:30080/api/v1"
```

---

## 5. 验证连接

### 5.1 在 Cline 中测试

打开 VS Code，点击左侧 Cline 图标打开聊天面板，输入：

```
调用 get_projects 工具，列出所有项目
```

预期输出示例：
```
[1] Burndown Management System - Scrum 项目管理系统
[2] Mobile App - 移动端应用
```

### 5.2 查看 MCP Server 日志

MCP Server 会在启动目录写入日志文件：

```bash
# 日志位置
D:/java/claude/projects/2/mcp-server/mcp-server.log

# 实时查看
tail -f D:/java/claude/projects/2/mcp-server/mcp-server.log
```

---

## 6. 使用示例

### 场景 1：每日站会

在 Cline 聊天面板中输入：

```
项目 ID 是 1，帮我生成今天的站会报告，包括进行中的任务和燃尽图状态
```

Cursor 会自动调用 `get_active_sprint_tasks` + `get_burndown_data` + `get_sprint_completion_probability` 并生成报告。

### 场景 2：燃尽图风险预警

```
Sprint 5 的燃尽图健康吗？有没有完不成的风险？
```

### 场景 3：创建任务

```
在项目 1 的当前 Sprint 中，帮我创建一个用户登录安全审查任务，HIGH 优先级，类型 TASK，3 故事点，先用 AI 生成描述
```

Cursor 会依次调用 `ai_generate_task_description` → `create_task` 并返回新建任务的 TaskKey。

### 场景 4：使用内置 Prompt

在 Cline 面板 → MCP Servers → scrum-standup → Prompts，可直接点击运行注册的 Prompt 模板：
- `daily_standup` — 一键生成站会报告
- `sprint_health_check` — Sprint 健康度检查
- `task_breakdown` — 需求拆解为子任务

---

## 7. 故障排查

### 问题 1：Cline 看不到 MCP 工具 / 状态显示 Disconnected

**原因：** 配置文件路径错误或 Server 启动失败。

**排查步骤：**
1. 确认 `dist/index.js` 存在：
   ```bash
   ls D:/java/claude/projects/2/mcp-server/dist/
   ```
2. 手动运行 Server 测试（PowerShell）：
   ```powershell
   cd D:/java/claude/projects/2/mcp-server
   $env:BACKEND_URL="http://localhost:8080/api/v1"
   $env:BACKEND_USERNAME="admin"
   $env:BACKEND_PASSWORD="your-password"
   node dist/index.js
   ```
   若无报错说明 Server 本身正常，问题在 Cline 配置。
3. 检查 `cline_mcp_settings.json` 中 `args` 路径是否正确，使用正斜杠 `/`。
4. 在 Cline MCP Servers 面板点击 **Retry** 按钮重新连接，无需重启 VS Code。
5. 查看 VS Code 输出面板（`Ctrl+Shift+U`）选择 **Cline** 查看详细错误日志。

### 问题 2：工具调用返回 "Backend error unreachable"

**原因：** 后端服务未启动或 URL 配置错误。

**解决：**
```bash
curl http://localhost:8080/api/v1/actuator/health
```
确认后端运行，或修改 `BACKEND_URL`。

### 问题 3：认证失败（401）

**原因：** 用户名/密码错误。

**解决：** 检查 `mcp.json` 中的 `BACKEND_USERNAME` / `BACKEND_PASSWORD`，确认与后端数据库中的用户一致。

### 问题 4：`ai_generate_task_description` 返回错误

**原因：** AI 功能未在后端启用，或未配置 LLM API Key。

**解决：** 检查后端 `application.yml`：
```yaml
ai:
  enabled: true
  api-key: "your-llm-api-key"
  base-url: "https://api.deepseek.com/v1"
```

### 问题 5：`search_similar_tasks` 返回空结果

**原因：** pgvector 向量索引未建立，或任务尚未生成 embedding。

**解决：** 调用后端 embedding 批量生成接口，或直接用 `ai_generate_task_description` 代替（它内部用关键词匹配）。

---

## 附录：工具一览表

| 工具名 | 功能 | 主要参数 |
|--------|------|----------|
| `get_projects` | 获取所有项目 | 无 |
| `get_active_sprint` | 获取活跃 Sprint | `projectId` |
| `get_active_sprint_tasks` | 获取 Sprint 任务 | `projectId`, `status?` |
| `get_burndown_data` | 获取燃尽图数据 | `sprintId` |
| `get_sprint_completion_probability` | 完成概率预测 | `sprintId` |
| `trigger_burndown_snapshot` | 触发燃尽快照 | `sprintId` |
| `create_task` | 创建任务 | `projectId`, `title`, `type` |
| `update_task_status` | 更新任务状态 | `taskId`, `status` |
| `ai_generate_task_description` | AI 生成描述 | `projectId`, `title`, `type` |
| `get_my_worklogs` | 我的工作日志 | 无 |
| `search_similar_tasks` | 语义搜索任务 | `query` |

---

*文档由 Claude Code 生成 | 持续监控，一步步显示*
