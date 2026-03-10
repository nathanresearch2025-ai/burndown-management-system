# AI Agent 工具调用示例 - Payload 集合

## 📋 目录

1. [调用 getInProgressTasks](#1-调用-getinprogresstasks)
2. [调用 getSprintBurndown](#2-调用-getsprintburndown)
3. [调用 evaluateBurndownRisk](#3-调用-evaluateburndownrisk)
4. [调用多个工具](#4-调用多个工具)

---

## 1. 调用 getInProgressTasks

### 触发条件
用户询问**任务相关**的问题

### Payload 示例

#### 示例 1.1：查询进行中的任务
```json
{
    "question": "我今天有哪些任务在进行中？",
    "projectId": 1,
    "sprintId": 1,
    "timezone": "Asia/Shanghai"
}
```

#### 示例 1.2：查询任务状态
```json
{
    "question": "我负责的任务进展如何？",
    "projectId": 1,
    "sprintId": 1,
    "timezone": "Asia/Shanghai"
}
```

#### 示例 1.3：查询工作内容
```json
{
    "question": "今天我在做什么工作？",
    "projectId": 1,
    "sprintId": 1,
    "timezone": "Asia/Shanghai"
}
```

**AI 会调用**：`getInProgressTasks`

**返回示例**：
```
找到 4 个进行中的任务：
- TASK-101: 实现用户登录功能 (优先级: HIGH, 故事点: 5, 更新时间: 2026-03-10)
- TASK-102: 优化数据库查询性能 (优先级: MEDIUM, 故事点: 3, 更新时间: 2026-03-10)
- TASK-103: 修复前端样式问题 (优先级: LOW, 故事点: 2, 更新时间: 2026-03-10)
- TASK-104: 编写 API 文档 (优先级: MEDIUM, 故事点: 3, 更新时间: 2026-03-10)
```

---

## 2. 调用 getSprintBurndown

### 触发条件
用户询问**Sprint 进度、燃尽图、剩余工作量**相关的问题

### Payload 示例

#### 示例 2.1：查询 Sprint 进度
```json
{
    "question": "当前 Sprint 的进度如何？",
    "projectId": 1,
    "sprintId": 1,
    "timezone": "Asia/Shanghai"
}
```

#### 示例 2.2：查询燃尽图情况
```json
{
    "question": "燃尽图的情况怎么样？",
    "projectId": 1,
    "sprintId": 1,
    "timezone": "Asia/Shanghai"
}
```

#### 示例 2.3：查询剩余工作量
```json
{
    "question": "还剩多少工作量没完成？",
    "projectId": 1,
    "sprintId": 1,
    "timezone": "Asia/Shanghai"
}
```

#### 示例 2.4：查询 Sprint 状态
```json
{
    "question": "我们的 Sprint 进展是否正常？",
    "projectId": 1,
    "sprintId": 1,
    "timezone": "Asia/Shanghai"
}
```

#### 示例 2.5：查询完成情况
```json
{
    "question": "团队完成了多少任务？",
    "projectId": 1,
    "sprintId": 1,
    "timezone": "Asia/Shanghai"
}
```

**AI 会调用**：`getSprintBurndown`

**返回示例**：
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

## 3. 调用 evaluateBurndownRisk

### 触发条件
用户询问**风险、延期、能否按时完成**相关的问题

### Payload 示例

#### 示例 3.1：查询风险
```json
{
    "question": "当前有什么风险吗？",
    "projectId": 1,
    "sprintId": 1,
    "timezone": "Asia/Shanghai"
}
```

#### 示例 3.2：查询是否会延期
```json
{
    "question": "我们会延期吗？",
    "projectId": 1,
    "sprintId": 1,
    "timezone": "Asia/Shanghai"
}
```

#### 示例 3.3：查询能否按时完成
```json
{
    "question": "能按时完成 Sprint 吗？",
    "projectId": 1,
    "sprintId": 1,
    "timezone": "Asia/Shanghai"
}
```

#### 示例 3.4：查询延期风险
```json
{
    "question": "有延期风险吗？",
    "projectId": 1,
    "sprintId": 1,
    "timezone": "Asia/Shanghai"
}
```

#### 示例 3.5：查询项目健康度
```json
{
    "question": "项目健康状况如何？",
    "projectId": 1,
    "sprintId": 1,
    "timezone": "Asia/Shanghai"
}
```

**AI 会调用**：`evaluateBurndownRisk`

**返回示例**：
```
风险等级: MEDIUM
偏差比例: 20.0%
偏差工时: 8.0 小时
建议: 存在中等延期风险，建议优先推进高优任务并减少并行工作
```

---

## 4. 调用多个工具

### 触发条件
用户询问**综合性问题**，需要多个数据源

### Payload 示例

#### 示例 4.1：站会总结（调用所有工具）
```json
{
    "question": "帮我总结一下今天的站会情况",
    "projectId": 1,
    "sprintId": 1,
    "timezone": "Asia/Shanghai"
}
```

**AI 会调用**：
1. `getInProgressTasks` - 获取进行中的任务
2. `getSprintBurndown` - 获取 Sprint 进度
3. `evaluateBurndownRisk` - 评估风险

**AI 综合回答示例**：
```
站会总结：

【任务进展】
您当前有 4 个任务正在进行中：
- TASK-101: 实现用户登录功能 (HIGH, 5点)
- TASK-102: 优化数据库查询性能 (MEDIUM, 3点)
- TASK-103: 修复前端样式问题 (LOW, 2点)
- TASK-104: 编写 API 文档 (MEDIUM, 3点)

【Sprint 进度】
Sprint 1 进展情况：
- 已完成任务：12/20 (60%)
- 进行中任务：5 个
- 计划剩余工时：40.0 小时
- 实际剩余工时：48.0 小时
- 偏差：+8.0 小时

【风险评估】
风险等级：MEDIUM (中等风险)
偏差比例：20.0%
建议：存在中等延期风险，建议优先推进高优任务并减少并行工作

【总结】
团队整体进度略有延后，建议聚焦高优先级任务，减少并行工作，确保按时完成 Sprint 目标。
```

#### 示例 4.2：项目状态报告
```json
{
    "question": "给我一份项目状态报告",
    "projectId": 1,
    "sprintId": 1,
    "timezone": "Asia/Shanghai"
}
```

**AI 会调用**：
1. `getSprintBurndown` - 获取进度数据
2. `evaluateBurndownRisk` - 评估风险

#### 示例 4.3：团队效率分析
```json
{
    "question": "团队效率怎么样？有什么问题吗？",
    "projectId": 1,
    "sprintId": 1,
    "timezone": "Asia/Shanghai"
}
```

**AI 会调用**：
1. `getInProgressTasks` - 获取任务分布
2. `getSprintBurndown` - 获取完成情况
3. `evaluateBurndownRisk` - 评估效率风险

#### 示例 4.4：每日站会问答
```json
{
    "question": "我昨天做了什么？今天计划做什么？有什么阻碍吗？",
    "projectId": 1,
    "sprintId": 1,
    "timezone": "Asia/Shanghai"
}
```

**AI 会调用**：
1. `getInProgressTasks` - 获取任务列表
2. `getSprintBurndown` - 获取整体进度
3. `evaluateBurndownRisk` - 识别潜在阻碍

---

## 🔍 工具选择逻辑

### getInProgressTasks
**关键词**：任务、进行中、工作、负责、做什么

**触发场景**：
- 查询个人任务
- 查询工作内容
- 查询任务状态

### getSprintBurndown
**关键词**：Sprint、进度、燃尽图、剩余、完成、工作量

**触发场景**：
- 查询 Sprint 进度
- 查询燃尽图数据
- 查询剩余工作量
- 查询完成情况

### evaluateBurndownRisk
**关键词**：风险、延期、按时、健康、问题、阻碍

**触发场景**：
- 风险评估
- 延期判断
- 项目健康度检查
- 问题识别

---

## 📝 测试建议

### 1. 单工具测试
逐个测试每个工具，确保工具函数正常工作

### 2. 多工具测试
测试需要调用多个工具的综合性问题

### 3. 边界测试
- 没有任务的情况
- 没有燃尽图数据的情况
- 风险等级边界值测试

### 4. 自然语言变化测试
用不同的表述方式问同一个问题，测试 AI 的理解能力

---

## 🎯 Postman 测试步骤

### 步骤 1：登录获取 Token
```bash
POST http://localhost:8080/api/v1/auth/login
Content-Type: application/json

{
    "username": "admin",
    "password": "admin123"
}
```

### 步骤 2：测试单个工具
选择上面的任意 payload，发送请求：

```bash
POST http://localhost:8080/api/v1/agent/standup/query
Content-Type: application/json
Authorization: Bearer {your_token}

{payload_here}
```

### 步骤 3：查看响应
检查响应中的 `toolsUsed` 字段，确认 AI 调用了哪些工具

---

**创建时间**：2026-03-10
**文件位置**：`AI_AGENT_PAYLOAD_EXAMPLES.md`
