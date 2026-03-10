# Burndown Management API - Postman 使用指南

## 📦 文件说明

本目录包含以下文件：

1. **Burndown-AI-Agent-API.postman_collection.json** - API 接口集合
   - 登录认证接口
   - AI Agent 站会助手接口

2. **Burndown-Local-Environment.postman_environment.json** - 本地环境变量配置

## 🚀 快速开始

### 1. 导入到 Postman

#### 方式一：通过 Postman 应用导入
1. 打开 Postman 应用
2. 点击左上角 **Import** 按钮
3. 选择 **File** 标签
4. 拖拽或选择以下文件：
   - `Burndown-AI-Agent-API.postman_collection.json`
   - `Burndown-Local-Environment.postman_environment.json`
5. 点击 **Import** 完成导入

#### 方式二：通过文件夹导入
1. 打开 Postman
2. 点击 **Import** → **Folder**
3. 选择 `postman` 目录
4. Postman 会自动识别并导入所有文件

### 2. 配置环境变量

1. 在 Postman 右上角选择环境：**Burndown Local Environment**
2. 点击环境名称旁边的眼睛图标，查看环境变量
3. 确认 `base_url` 为：`http://localhost:8080/api/v1`

### 3. 启动后端服务

确保后端服务已启动：

```bash
cd backend
mvn spring-boot:run
```

后端服务运行在：`http://localhost:8080`

### 4. 测试接口

#### 步骤 1：登录获取 Token

1. 展开 **Authentication** 文件夹
2. 选择 **Login** 请求
3. 点击 **Send** 发送请求
4. 登录成功后，JWT Token 会自动保存到环境变量 `jwt_token` 中

**默认测试账号：**
- 用户名: `admin`
- 密码: `admin123`

#### 步骤 2：测试 AI Agent 接口

1. 展开 **AI Agent - Standup Assistant** 文件夹
2. 选择任意一个问答请求，例如：
   - **Standup Query - 我今天有哪些任务**
   - **Standup Query - Sprint 燃尽图分析**
3. 请求会自动使用步骤 1 保存的 JWT Token
4. 点击 **Send** 发送请求

## 📋 接口列表

### 1. Authentication（认证）

#### POST /auth/login - 用户登录
- **功能**：用户登录，获取 JWT Token
- **请求体**：
  ```json
  {
    "username": "admin",
    "password": "admin123"
  }
  ```
- **响应**：
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "user": {
      "id": 1,
      "username": "admin",
      "email": "admin@example.com"
    }
  }
  ```

### 2. AI Agent - Standup Assistant（站会助手）

#### POST /agent/standup/query - 站会问答

**功能说明：**
- 基于 Spring AI 框架的智能问答
- 支持自然语言提问
- AI 会自动调用工具函数获取数据（Function Calling）
- 返回智能分析结果

**请求体：**
```json
{
  "question": "我今天有哪些任务在进行中？",
  "projectId": 1,
  "sprintId": 1,
  "timezone": "Asia/Shanghai"
}
```

**参数说明：**
- `question` (必填): 用户的问题
- `projectId` (必填): 项目 ID
- `sprintId` (可选): Sprint ID
- `timezone` (可选): 时区，默认 Asia/Shanghai

**响应示例：**
```json
{
  "success": true,
  "data": {
    "answer": "您当前有 3 个任务正在进行中：\n1. TASK-101: 实现用户登录功能 (优先级: HIGH, 故事点: 5)\n2. TASK-102: 优化数据库查询性能 (优先级: MEDIUM, 故事点: 3)\n3. TASK-103: 修复前端样式问题 (优先级: LOW, 故事点: 2)",
    "summary": {
      "totalTasks": 3,
      "riskLevel": "LOW"
    },
    "toolsUsed": ["getInProgressTasks"],
    "evidence": []
  },
  "traceId": "abc123def456"
}
```

**支持的问题类型：**

1. **任务查询**
   - "我今天有哪些任务在进行中？"
   - "我负责的高优先级任务有哪些？"
   - "还有多少任务没完成？"

2. **进度查询**
   - "当前 Sprint 的进度如何？"
   - "还剩多少工作量？"
   - "我们能按时完成吗？"

3. **燃尽图分析**
   - "当前 Sprint 的燃尽图情况如何？"
   - "燃尽图趋势正常吗？"
   - "有延期风险吗？"

4. **风险评估**
   - "当前有什么风险？"
   - "需要关注哪些问题？"
   - "团队效率怎么样？"

**AI 可用工具：**

1. **getInProgressTasks** - 获取进行中的任务
   - 查询指定项目和用户的进行中任务
   - 返回任务编号、标题、优先级、故事点等信息

2. **getSprintBurndown** - 获取 Sprint 燃尽图数据
   - 查询 Sprint 的每日剩余工作量
   - 返回燃尽图数据点列表

3. **evaluateBurndownRisk** - 评估燃尽图风险
   - 分析 Sprint 进度和风险
   - 返回风险等级和建议

## 🔧 环境变量说明

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `base_url` | API 基础 URL | `http://localhost:8080/api/v1` |
| `jwt_token` | JWT 认证令牌 | 自动保存 |
| `project_id` | 项目 ID | `1` |
| `sprint_id` | Sprint ID | `1` |
| `user_id` | 用户 ID | `1` |

## 📝 使用技巧

### 1. 自动保存 Token

登录接口包含自动保存 Token 的脚本：

```javascript
if (pm.response.code === 200) {
    var jsonData = pm.response.json();
    if (jsonData.token) {
        pm.environment.set("jwt_token", jsonData.token);
        console.log("Token saved: " + jsonData.token);
    }
}
```

登录成功后，Token 会自动保存到环境变量，后续请求会自动使用。

### 2. 修改问题内容

在 **Standup Query - 自定义问题** 请求中：
1. 修改 `question` 字段为你的问题
2. 调整 `projectId` 和 `sprintId`
3. 发送请求

### 3. 查看响应详情

- 点击 **Body** 标签查看响应内容
- 点击 **Headers** 标签查看响应头
- 点击 **Test Results** 查看测试脚本执行结果

## 🐛 常见问题

### 1. 401 Unauthorized 错误

**原因**：JWT Token 未设置或已过期

**解决方案**：
1. 重新执行 **Login** 请求获取新 Token
2. 确认环境变量 `jwt_token` 已正确设置
3. 检查请求头是否包含：`Authorization: Bearer {{jwt_token}}`

### 2. 404 Not Found 错误

**原因**：后端服务未启动或 URL 配置错误

**解决方案**：
1. 确认后端服务已启动：`mvn spring-boot:run`
2. 检查 `base_url` 环境变量是否正确
3. 确认端口号为 8080

### 3. AI Agent 返回错误

**原因**：数据库中没有数据或配置错误

**解决方案**：
1. 确认数据库已初始化（运行 `init.sql`）
2. 检查 `projectId` 和 `sprintId` 是否存在
3. 查看后端日志排查问题

### 4. 连接超时

**原因**：网络问题或服务响应慢

**解决方案**：
1. 检查网络连接
2. 增加 Postman 的超时时间：Settings → General → Request timeout
3. 检查后端服务是否正常运行

## 📚 相关文档

- [后端 API 文档](http://localhost:8080/api/v1/swagger-ui.html)
- [项目 README](../README.md)
- [AI Agent 技术设计文档](../docs/ai-agent/)

## 🎯 测试场景示例

### 场景 1：日常站会

1. 登录系统
2. 询问："我今天有哪些任务在进行中？"
3. 询问："当前 Sprint 的进度如何？"
4. 询问："有什么风险需要关注吗？"

### 场景 2：Sprint 回顾

1. 登录系统
2. 询问："当前 Sprint 的燃尽图情况如何？"
3. 询问："团队整体完成情况怎么样？"
4. 询问："有哪些改进建议？"

### 场景 3：风险评估

1. 登录系统
2. 询问："当前有什么风险？"
3. 询问："我们能按时完成 Sprint 吗？"
4. 询问："需要采取什么措施？"

## 💡 提示

- 首次使用前，确保数据库已初始化并包含测试数据
- AI Agent 的回答质量取决于数据的完整性
- 可以尝试不同的问题表述方式，AI 会理解并给出相应回答
- 查看后端日志可以了解 AI 调用了哪些工具函数

---

**版本**: 1.0.0
**更新日期**: 2026-03-10
**维护者**: Burndown Development Team
