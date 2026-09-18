# MCP Scrum Standup Server（Python 版）启动文档

## 目录结构

```
mcp-server-python/
├── main.py                  # MCP Server 入口
├── requirements.txt         # Python 依赖
├── .env.example             # 环境变量示例
├── mcp-config.example.json  # 客户端配置示例
├── client/
│   └── api.py               # HTTP 客户端（JWT 自动管理）
├── tools/
│   ├── sprint.py            # get_projects / get_active_sprint / get_active_sprint_tasks
│   ├── burndown.py          # get_burndown_data / get_sprint_completion_probability / trigger_burndown_snapshot
│   ├── task.py              # create_task / update_task_status / ai_generate_task_description / get_my_worklogs
│   └── search.py            # search_similar_tasks
├── resources/
│   └── board.py             # scrum://projects / scrum://sprint/{id}/board 等
└── prompts/
    └── standup.py           # daily_standup / sprint_health_check / task_breakdown
```

---

## 一、环境准备

### 1.1 确认 Python 版本

```bash
python --version
# 需要 Python 3.11+
```

### 1.2 创建虚拟环境（推荐）

```bash
cd D:/java/claude/projects/2/mcp-server-python

python -m venv .venv

# Windows
.venv\Scripts\activate

# macOS / Linux
source .venv/bin/activate
```

### 1.3 安装依赖

```bash
pip install -r requirements.txt
```

依赖说明：
| 包 | 作用 |
|---|---|
| `mcp>=1.0.0` | Anthropic MCP SDK，提供 Server/Tool/Resource/Prompt 框架 |
| `httpx>=0.27.0` | 异步 HTTP 客户端，调用后端 REST API |
| `python-dotenv>=1.0.0` | 从 `.env` 文件加载环境变量 |

---

## 二、配置环境变量

```bash
# 复制示例文件
cp .env.example .env

# 编辑 .env（填入实际值）
```

`.env` 内容：
```
BACKEND_URL=http://localhost:8080/api/v1
BACKEND_USERNAME=admin
BACKEND_PASSWORD=password123
LOG_LEVEL=INFO
```

---

## 三、启动 MCP Server

### 3.1 手动测试启动

```bash
cd D:/java/claude/projects/2/mcp-server-python
python main.py
```

正常输出：
```
2026-03-25 [INFO] mcp-scrum: MCP Scrum Standup Server (Python) 启动中...
2026-03-25 [INFO] mcp-scrum: 后端地址: http://localhost:8080/api/v1
2026-03-25 [INFO] mcp-scrum: 已注册 11 个 Tool，4 个 Resource，3 个 Prompt
2026-03-25 [INFO] mcp-scrum: MCP Server 已连接（stdio），等待请求...
```

> MCP Server 使用 **stdio 传输**，启动后等待客户端连接，不会有 HTTP 端口监听。

### 3.2 使用 MCP Inspector 测试（Web UI）

```bash
npx @modelcontextprotocol/inspector python D:/java/claude/projects/2/mcp-server-python/main.py
```

浏览器打开 `http://localhost:5173`，可以：
- 查看所有 11 个工具
- 填入参数，点击 Execute 调用
- 查看返回的 JSON / 文本结果

---

## 四、客户端配置

### 4.1 Cursor

编辑文件：`C:/Users/<用户名>/.cursor/mcp.json`

```json
{
  "mcpServers": {
    "scrum-standup-py": {
      "command": "python",
      "args": ["D:/java/claude/projects/2/mcp-server-python/main.py"],
      "env": {
        "BACKEND_URL": "http://localhost:8080/api/v1",
        "BACKEND_USERNAME": "admin",
        "BACKEND_PASSWORD": "password123",
        "LOG_LEVEL": "INFO"
      },
      "disabled": false,
      "timeout": 60,
      "autoApprove": [
        "get_projects",
        "get_active_sprint",
        "get_active_sprint_tasks",
        "get_burndown_data",
        "get_sprint_completion_probability",
        "trigger_burndown_snapshot",
        "create_task",
        "update_task_status",
        "ai_generate_task_description",
        "get_my_worklogs",
        "search_similar_tasks"
      ]
    }
  }
}
```

**注意：** 如果使用虚拟环境，`command` 改为虚拟环境中的 Python 路径：
```json
"command": "D:/java/claude/projects/2/mcp-server-python/.venv/Scripts/python.exe"
```

### 4.2 VS Code + Cline 插件

编辑文件：
`C:/Users/<用户名>/AppData/Roaming/Code/User/globalStorage/saoudrizwan.claude-dev/settings/cline_mcp_settings.json`

内容与 Cursor 配置相同（`mcpServers` 对象格式一致）。

### 4.3 Claude Desktop

编辑文件：
- Windows：`%APPDATA%\Claude\claude_desktop_config.json`
- macOS：`~/Library/Application Support/Claude/claude_desktop_config.json`

内容与 Cursor 配置相同。

---

## 五、工具清单

| 工具名 | 描述 | 对应接口 |
|--------|------|---------|
| `get_projects` | 获取所有项目 | `GET /projects` |
| `get_active_sprint` | 获取项目活跃 Sprint | `GET /sprints/project/{id}` |
| `get_active_sprint_tasks` | 获取 Sprint 任务（可按状态过滤） | `GET /tasks/sprint/{id}` |
| `get_burndown_data` | 获取燃尽图数据 | `GET /burndown/sprints/{id}` |
| `get_sprint_completion_probability` | ML 预测完成概率 | `GET /sprints/{id}/completion-probability` |
| `trigger_burndown_snapshot` | 触发燃尽图快照 | `POST /burndown/sprints/{id}/calculate` |
| `create_task` | 创建任务 | `POST /tasks` |
| `update_task_status` | 更新任务状态 | `PATCH /tasks/{id}/status` |
| `ai_generate_task_description` | AI 生成任务描述 | `POST /tasks/ai/generate-description` |
| `get_my_worklogs` | 获取我的工作日志 | `GET /worklogs/my-worklogs` |
| `search_similar_tasks` | 语义搜索相似任务 | `POST /similarity/search` |

---

## 六、使用示例

配置完成后，在 Cursor / Cline 中直接提问：

```
Sprint 2 今天进行中的任务有哪些？
```
→ 自动调用 `get_active_sprint_tasks(sprint_id=2, status=IN_PROGRESS)`

```
当前 Sprint 的燃尽图健康吗？有没有风险？
```
→ 自动调用 `get_burndown_data` + `get_sprint_completion_probability`

```
帮我为用户登录功能创建一个安全审查任务，HIGH 优先级，3 故事点，放到 Sprint 2
```
→ 自动调用 `ai_generate_task_description` + `create_task`

---

## 七、故障排查

### 启动失败：ModuleNotFoundError

```bash
# 确认虚拟环境已激活，再安装
pip install -r requirements.txt
```

### 工具调用报认证错误

检查 `.env` 或客户端配置中的 `BACKEND_PASSWORD` 是否正确（默认 `password123`）。

### 工具调用超时

- 检查后端是否运行：`curl http://localhost:8080/api/v1/actuator/health`
- 增大客户端配置中的 `timeout`（单位秒，默认 60）

### Cursor 找不到 python 命令

使用虚拟环境的完整路径：
```json
"command": "D:/java/claude/projects/2/mcp-server-python/.venv/Scripts/python.exe"
```

---

## 八、与 TypeScript 版本对比

| 对比项 | TypeScript 版（mcp-server/） | Python 版（mcp-server-python/） |
|--------|-----------------------------|---------------------------------|
| 运行时 | Node.js | Python 3.11+ |
| 依赖 | npm install | pip install |
| 启动命令 | `node dist/index.js` | `python main.py` |
| 与 LangChain 集成 | 需要额外封装 | 原生支持 |
| 编译步骤 | 需要 tsc 编译 | 直接运行，无需编译 |

---

*文档由 Claude Code 生成 | 持续监控，一步步显示*
