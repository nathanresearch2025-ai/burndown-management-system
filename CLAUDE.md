# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Burndown Management System - 一个全栈 Scrum 项目管理应用，具有燃尽图可视化功能，使用 Spring Boot 3.2 (Java 21) 后端和 React 18 (TypeScript) 前端构建。

**技术栈：**
- 后端：Spring Boot 3.2, Java 21, PostgreSQL 16, Redis 7, Spring Security + JWT
- 前端：React 18, TypeScript 5, Ant Design 5, Vite 5, Zustand, React Query, ECharts
- 部署：Kubernetes, Docker
- 监控：Prometheus, Grafana, Alertmanager

## 开发命令

### 后端 (Spring Boot)

```bash
cd backend

# 本地运行（需要 PostgreSQL 和 Redis 运行）
mvn spring-boot:run

# 构建 JAR
mvn clean package

# 运行测试
mvn test

# 构建时跳过测试
mvn clean package -DskipTests
```

后端运行在 `http://localhost:8080`，上下文路径为 `/api/v1`
- API 文档：`http://localhost:8080/api/v1/swagger-ui.html`
- 监控指标：`http://localhost:8080/api/v1/actuator/prometheus`

### 前端 (React + Vite)

```bash
cd frontend

# 安装依赖
npm install

# 运行开发服务器
npm run dev

# 生产环境构建
npm run build

# 预览生产构建
npm run preview
```

前端开发服务器运行在 `http://localhost:5173`

### 数据库设置

```bash
# 创建数据库
psql -U postgres -c "CREATE DATABASE burndown_db;"

# 启用 pgvector 扩展（必须，任务实体使用 vector(384) 列）
psql -U postgres -d burndown_db -c "CREATE EXTENSION IF NOT EXISTS vector;"

# 初始化 schema 和数据
psql -U postgres -d burndown_db -f backend/init.sql
```

**重要：** `application.yml` 中的数据源默认指向远程 K8s PostgreSQL（`159.75.202.106:30432`）和 Redis（`159.75.202.106:30379`）。本地开发时需要覆盖这些地址，可通过环境变量或 `application-local.yml` Profile。

### 部署

```bash
# 完整部署（不使用缓存重新构建镜像）
./deploy.sh

# 快速部署（使用 Docker 缓存）
./deploy.sh --cache

# 仅部署监控
./deploy.sh --monitoring-only
```

部署脚本执行：
1. 为后端和前端构建 Docker 镜像
2. 将 PostgreSQL、后端和前端部署到 Kubernetes
3. 使用 schema 和示例数据初始化数据库
4. 可选部署 Prometheus、Grafana 和 Alertmanager

访问已部署的服务：
- 前端：`http://<node-ip>:30173`
- 后端 API：`http://<node-ip>:30080`
- PostgreSQL：`<node-ip>:30432`
- Prometheus：`http://<node-ip>:30090`
- Grafana：`http://<node-ip>:30300` (admin/admin123)
- Alertmanager：`http://<node-ip>:30093`

## 架构

### 后端结构

```
backend/src/main/java/com/burndown/
├── config/          # Security、JWT、I18n 配置
├── controller/      # REST API 端点
├── dto/            # 请求/响应 DTOs
├── entity/         # JPA 实体（User、Project、Sprint、Task、WorkLog 等）
├── exception/      # 自定义异常和处理器
├── filter/         # JWT 认证过滤器
├── repository/     # Spring Data JPA 仓库
└── service/        # 业务逻辑层
```

**核心实体：**
- `User` - 用户账户，带有 RBAC（角色/权限）
- `Project` - Scrum 项目，包含所有者和设置
- `Sprint` - 时间盒迭代，带有容量跟踪
- `Task` - 工作项，包含状态、优先级、故事点
- `WorkLog` - 时间跟踪条目
- `BurndownPoint` - 每日燃尽图数据点

**认证：** 基于 JWT 的 Spring Security。Token 过期时间在 `application.yml` 中配置（默认 7 天）。

**数据库：** PostgreSQL 配合 JPA/Hibernate。Schema 通过 `init.sql` 管理（ddl-auto: none）。HikariCP 连接池（最大 20，最小 5）。

**监控：** Spring Boot Actuator 在 `/api/v1/actuator/prometheus` 暴露 Prometheus 指标。包括 JVM、HTTP、数据库连接池指标。

### 前端结构

```
frontend/src/
├── api/            # Axios API 客户端和端点定义
├── components/     # 可复用的 React 组件
│   ├── Layout/     # 应用布局组件
│   └── Modals/     # 模态对话框
├── hooks/          # 自定义 React hooks
├── i18n/           # 国际化（i18next）
│   └── locales/    # 翻译文件（en-US、zh-CN）
├── pages/          # 页面组件（Login、Dashboard、TaskBoard 等）
├── store/          # Zustand 状态管理
├── App.tsx         # 根组件，包含路由
└── main.tsx        # 应用入口点
```

**状态管理：** Zustand 用于全局状态，React Query 用于服务器状态缓存。

**路由：** React Router v6，带有需要认证的受保护路由。

**UI 组件：** Ant Design 5 组件库，带有自定义主题。

**图表：** ECharts 用于燃尽图可视化。

**拖放：** @hello-pangea/dnd 用于任务看板看板功能。

### 关键工作流

**燃尽图计算：**
1. `BurndownController` 计算每日剩余工作
2. 聚合未完成任务的故事点
3. 在 `burndown_points` 表中存储快照
4. 前端获取并使用 ECharts 可视化

**任务状态流：**
TODO → IN_PROGRESS → IN_REVIEW → DONE

**Sprint 生命周期：**
PLANNED → ACTIVE → COMPLETED

## 配置文件

- `backend/src/main/resources/application.yml` - 主 Spring Boot 配置（数据源、Redis、JWT、Actuator）
- `backend/src/main/resources/application-docker.yml` - Docker 特定覆盖
- `backend/init.sql` - 完整的数据库 schema 和示例数据
- `frontend/vite.config.ts` - Vite 构建配置
- `k8s/*.yaml` - Kubernetes 部署清单
- `monitoring/k8s-monitoring-all.yaml` - 完整监控栈

## 重要注意事项

- **CORS：** 后端在 `SecurityConfig.java` 中为特定来源配置。为新的前端 URL 更新 `setAllowedOrigins()`。
- **JWT Secret：** 在生产环境中更改 `jwt.secret`（最小 256 位）。
- **数据库持久化：** K8s 部署使用 `emptyDir` - pod 重启时数据丢失。生产环境使用 PersistentVolume。
- **主机网络：** K8s 部署使用 `hostNetwork: true` - 注意端口冲突。
- **Docker 缓存：** 代码更改后始终使用 `./deploy.sh`（无缓存）以确保新构建。
- **监控告警：** 预配置了高 CPU（>80%）、内存（>85%）、错误率（>5%）、慢响应（>2s）告警。

## 故障排除

**后端无法启动：**
```bash
# 检查 PostgreSQL 连接
kubectl exec -it <postgres-pod> -- psql -U postgres -d burndown_db

# 查看后端日志
kubectl logs -l app=burndown-backend

# 检查数据库是否已初始化
kubectl exec -it <postgres-pod> -- psql -U postgres -d burndown_db -c "\dt"
```

**前端 API 调用失败：**
```bash
# 检查后端健康状态
curl http://localhost:8080/api/v1/actuator/health

# 验证 SecurityConfig.java 中的 CORS 配置
# 检查前端和后端之间的网络连接
```

**部署问题：**
```bash
# 查看所有 pods
kubectl get pods

# 描述 pod 以查看事件
kubectl describe pod <pod-name>

# 检查镜像是否正确构建
docker images | grep burndown

# 强制不使用缓存重新构建
./deploy.sh
```

## AI Agent 架构（Standup 助手）

系统包含一个 Scrum 日常站会 AI 助手，基于 ReAct 模式构建：

**两套实现并存：**
1. **Java 原生 ReAct Agent**（`aiagent/standup/`）— Spring AI Function Calling，注册了 3 个工具函数，由 `StandupAgentService` 编排 ReAct 循环
2. **LangChain Python Sidecar**（`aiagent/langchain/`）— Java 通过 `LangchainClientService` 调用运行在 `:8091` 的 FastAPI 服务

**Agent 工具类（`aiagent/standup/tool/`）：**
- `StandupTaskTools` — 查询任务状态、阻塞项
- `StandupBurndownTools` — 查询燃尽图数据点、偏差计算
- `StandupRiskTools` — 风险评估

**Agent 端点：**
- Java 原生：`POST /api/v1/agent/standup/query`
- LangChain 代理：`POST /api/v1/langchain/standup/query`
- LangChain 工具回调：`POST /api/v1/agent/tools/*`（Python sidecar 回调此端点获取数据）

**会话持久化：** `AgentChatSession`、`AgentChatMessage`、`AgentToolCallLog` 三张表记录对话和工具调用历史。

**监控：** `StandupAgentMetrics` 注册 Micrometer 计数器/计时器，暴露至 Prometheus。

**LangChain Python Sidecar 启动（如需本地运行）：**
```bash
# 在 langchain-python/ 目录下（如果存在）
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8091
```

## AI 任务生成功能

系统包含使用 RAG（检索增强生成）的 AI 驱动任务描述生成功能：

**后端实现：**
- `TaskAiService` - 核心 RAG 工作流：使用基于关键词的相似度评分检索相似任务，使用项目上下文构建提示，并调用 LLM
- `AiClientService` - 外部 LLM API 的 HTTP 客户端（OpenAI 兼容格式）
- `AiTaskGenerationLog` 实体 - 跟踪所有生成请求及元数据
- 相似度算法：结合标题关键词匹配（55%）、任务类型（25%）、优先级（15%）和故事点（5%）

**配置（application.yml）：**
```yaml
ai:
  enabled: false          # 切换 AI 功能
  base-url: ""           # LLM API 端点
  api-key: ""            # API 认证密钥
  chat-model: ""         # 模型名称（例如 gpt-4、claude-3-5-sonnet）
  timeout: 30s           # 请求超时
  max-similar-tasks: 5   # 要检索的参考任务数量
```

**API 端点：**
```
POST /api/v1/tasks/ai/generate-description
{
  "projectId": 1,
  "title": "用户登录优化",
  "type": "FEATURE",
  "priority": "HIGH",
  "storyPoints": 5
}
```

**当前限制：**
- 未实现 Redis 缓存
- 未实现速率限制
- 前端 UI 集成待完成

## ML Sprint 预测功能

**Random Forest 模型（scikit-learn）：**
- 模型文件：`backend/src/main/resources/models/random_forest_model.pkl`
- 特征配置：`backend/src/main/resources/models/feature_columns.json`
- `PythonModelService` 在启动时（`@PostConstruct`）将模型文件解压到临时目录，并生成内联 Python 推理脚本，通过 `ProcessBuilder` 调用 Python 进程执行推理
- **Python 可执行路径配置**（`application.yml`）：
  ```yaml
  ml:
    python:
      executable: python  # 默认值；Windows 本地开发可能需要改为完整路径
  ```
- 预测端点：`GET /api/v1/sprints/{id}/predict`
- 训练代码：`docs/ml/randomForest/train_model.py` 和 `docs/ml/randomForest/sprint_rf_training.ipynb`

**输入特征（11 个）：** `sprint_days`, `days_elapsed`, `committed_sp`, `remaining_sp`, `completed_sp`, `velocity_current`, `velocity_avg_5`, `velocity_std_5`, `blocked_stories`, `attendance_rate`, `ratio_feature`

## 向量相似度搜索

`Task` 实体包含 `vector(384)` 列（需要 PostgreSQL `pgvector` 扩展）。

**Embedding 提供者（`ai.embedding.provider`）：**
- `simple`（默认）：本地 TF-IDF + Hashing，无外部依赖
- `djl`：Deep Java Library 本地模型
- `api`：外部 Embedding API

`UnifiedEmbeddingService` 仅在 `ai.enabled=true` 时激活（`@ConditionalOnProperty`）。`VectorSimilarityService` 使用 pgvector 的 `<->` 运算符执行近邻查询。

## 异常处理

带有 i18n 支持的自定义异常层次结构：
- `BusinessException` - 业务逻辑错误，带有错误代码、i18n 消息键和 HTTP 状态
- `ResourceNotFoundException` - 缺失实体的 404 错误
- `GlobalExceptionHandler` - 集中式异常处理，带有本地化错误消息

错误消息存储在：
- `backend/src/main/resources/i18n/messages_en_US.properties`
- `backend/src/main/resources/i18n/messages_zh_CN.properties`

## 安全架构

**认证流程：**
1. 用户通过 `/auth/login` 使用用户名/密码登录
2. 后端验证凭据并生成 JWT token（7 天过期）
3. Token 包含用户 ID、用户名和权限数组
4. 前端将 token 存储在 localStorage 中，并在 Authorization header 中包含
5. `JwtAuthenticationFilter` 在每个请求上验证 token

**授权：**
- RBAC 系统，包含 `User`、`Role`、`Permission` 和 `UserRole` 实体
- 权限嵌入在 JWT token 中以进行快速访问检查
- 前端从 token 解析权限以进行 UI 级授权
- 后端使用 `@PreAuthorize` 注解进行方法级安全

**安全注意事项：**
- BCrypt 密码哈希，强度为 4（用于测试；生产环境使用 6-8）
- CORS 配置为允许所有来源（开发模式）
- 除 `/auth/**` 外的所有端点都需要认证（当前为测试禁用）
- JWT secret 必须在生产环境中更改（最小 256 位）

## 数据库 Schema 管理

**关键规则：** 所有 schema 更改必须添加到 `backend/init.sql`

应用使用 `ddl-auto: none`，意味着 Hibernate 不会自动生成 schema。`init.sql` 文件是以下内容的唯一真实来源：
- 表定义
- 索引和约束
- 示例数据
- K8s 部署中的数据库初始化

添加新实体或修改现有实体时：
1. 更新 JPA 实体类
2. 将相应的 DDL 添加到 `init.sql`
3. 使用全新的数据库初始化进行测试

## 测试和性能

**测试资源：**
- 压力测试脚本：`test/pressure/scenario_pressure_test.py`
- 压力测试报告：`test/pressure/summary_report.html`
- 完整 API 测试套件：`test/all-interface-test/api_test.py`
- 性能优化文档：`docs/performance/`

**AI 功能文档：**
- PRD 和需求：`docs/ai-agent/`

## 前端 API 集成

所有 API 调用通过集中式 Axios 实例（`frontend/src/api/axios.ts`）进行，具有：
- 指向后端的基础 URL 配置
- 从 localStorage 自动注入 JWT token
- 用于错误处理的请求/响应拦截器
- 用于类型安全的 TypeScript 接口

按域组织的 API 模块：
- `auth.ts` - 认证端点
- `task.ts` - 任务 CRUD 和 AI 生成
- `project.ts`、`sprint.ts`、`worklog.ts` - 其他域 API

## 状态管理模式

**Zustand Store 结构：**
- `authStore.ts` - 用户认证状态、token 管理、权限
- Store 最小化，仅关注全局状态
- React Query 用于服务器状态缓存（不是 Zustand）
- 尽可能优先使用本地组件状态而不是全局状态

**权限处理：**
- 登录时从 JWT token 解析权限
- 存储在 Zustand 中以进行响应式 UI 更新
- 用于 UI 元素的条件渲染

- 需求文档目录：`docs/`（AI Agent 相关文档在 `docs/ai-agent/`，ML 相关在 `docs/ml/`）
- 后端代码目录：`backend/src/main/java/com/burndown/`
