# 插入测试任务数据 - 使用说明

## 📋 目的

为 userId=1 的用户插入进行中的任务数据，以便测试 AI Agent 的 `getInProgressTasks` 工具。

## 📊 插入的数据

### 进行中的任务（IN_PROGRESS）- 共 4 条

| 任务编号 | 标题 | 类型 | 优先级 | 故事点 | 更新时间 |
|---------|------|------|--------|--------|----------|
| TASK-101 | 实现用户登录功能 | FEATURE | HIGH | 5 | 今天 |
| TASK-102 | 优化数据库查询性能 | IMPROVEMENT | MEDIUM | 3 | 今天 |
| TASK-103 | 修复前端样式问题 | BUG | LOW | 2 | 今天 |
| TASK-104 | 编写 API 文档 | DOCUMENTATION | MEDIUM | 3 | 今天 |

### 其他状态的任务（用于对比）

- **TASK-105**：实现任务拖拽功能（TODO 状态）
- **TASK-100**：搭建项目基础架构（DONE 状态）

## 🚀 执行方式

### 方式 1：使用 psql 命令行（推荐）

```bash
# Windows PowerShell
$env:PGPASSWORD="root"
psql -h 159.75.202.106 -p 30432 -U postgres -d burndown_db -f insert_tasks_for_user1.sql

# Linux/Mac
PGPASSWORD=root psql -h 159.75.202.106 -p 30432 -U postgres -d burndown_db -f insert_tasks_for_user1.sql
```

### 方式 2：使用 DBeaver / DataGrip 等数据库工具

1. 连接到数据库：
   - Host: `159.75.202.106`
   - Port: `30432`
   - Database: `burndown_db`
   - Username: `postgres`
   - Password: `root`

2. 打开 `insert_tasks_for_user1.sql` 文件

3. 执行整个脚本

### 方式 3：使用 pgAdmin

1. 连接到数据库服务器
2. 选择 `burndown_db` 数据库
3. 打开 Query Tool
4. 粘贴 `insert_tasks_for_user1.sql` 的内容
5. 点击执行按钮

## ✅ 验证数据

执行以下 SQL 验证数据是否插入成功：

```sql
-- 查询 userId=1 的进行中任务
SELECT
    task_key,
    title,
    type,
    status,
    priority,
    story_points,
    updated_at
FROM task
WHERE assignee_id = 1
  AND status = 'IN_PROGRESS'
  AND project_id = 1
ORDER BY updated_at DESC;
```

**期望结果**：应该返回 4 条记录

## 🧪 测试 AI Agent

数据插入成功后，使用 Postman 测试：

### 1. 登录获取 Token

```bash
POST http://localhost:8080/api/v1/auth/login
Content-Type: application/json

{
    "username": "admin",
    "password": "admin123"
}
```

### 2. 调用 AI Agent 接口

```bash
POST http://localhost:8080/api/v1/agent/standup/query
Content-Type: application/json
Authorization: Bearer {your_token}

{
    "question": "我今天有哪些任务在进行中？",
    "projectId": 1,
    "sprintId": 1,
    "timezone": "Asia/Shanghai"
}
```

### 3. 期望的 AI 回答

```json
{
    "success": true,
    "data": {
        "answer": "您当前有 4 个任务正在进行中：\n1. TASK-101: 实现用户登录功能 (优先级: HIGH, 故事点: 5)\n2. TASK-102: 优化数据库查询性能 (优先级: MEDIUM, 故事点: 3)\n3. TASK-103: 修复前端样式问题 (优先级: LOW, 故事点: 2)\n4. TASK-104: 编写 API 文档 (优先级: MEDIUM, 故事点: 3)",
        "summary": {
            "totalTasks": 4,
            "riskLevel": "LOW"
        },
        "toolsUsed": ["getInProgressTasks"],
        "evidence": []
    },
    "traceId": "..."
}
```

## 🔍 工具调用流程

1. **用户提问**：我今天有哪些任务在进行中？
2. **AI 分析**：需要查询任务数据
3. **选择工具**：`getInProgressTasks`
4. **工具执行**：
   ```java
   taskRepository.findByProjectId(1)
       .filter(task -> task.getAssigneeId() == 1)
       .filter(task -> task.getStatus() == IN_PROGRESS)
   ```
5. **返回数据**：4 条进行中的任务
6. **AI 生成回答**：整合数据，生成自然语言回答

## 📝 数据说明

### 任务类型（type）
- `FEATURE`：新功能
- `BUG`：缺陷修复
- `IMPROVEMENT`：改进优化
- `DOCUMENTATION`：文档编写

### 任务状态（status）
- `TODO`：待办
- `IN_PROGRESS`：进行中
- `IN_REVIEW`：审核中
- `DONE`：已完成

### 优先级（priority）
- `HIGH`：高优先级
- `MEDIUM`：中优先级
- `LOW`：低优先级

## 🗑️ 清理数据

如果需要清理测试数据：

```sql
-- 删除插入的任务
DELETE FROM task WHERE task_key IN ('TASK-100', 'TASK-101', 'TASK-102', 'TASK-103', 'TASK-104', 'TASK-105');

-- 删除 Sprint（如果需要）
DELETE FROM sprint WHERE id = 1;

-- 删除项目（如果需要）
DELETE FROM project WHERE id = 1;
```

## 💡 提示

- 确保数据库中已存在 userId=1 的用户
- 确保 Spring Boot 应用已启动
- 确保已配置正确的数据库连接信息
- 如果任务已存在，SQL 会报错，可以先删除再插入

---

**文件位置**：`insert_tasks_for_user1.sql`
**创建时间**：2026-03-10
