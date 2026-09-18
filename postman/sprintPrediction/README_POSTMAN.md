# Postman 测试指南

## 导入 Postman 集合

1. 打开 Postman
2. 点击左上角 **Import** 按钮
3. 选择 `postman_collection.json` 文件
4. 导入完成后，集合名称为 "Burndown Management System - Sprint Prediction API"

## 使用步骤

### 1. 配置环境变量（可选）

集合已预配置以下变量，可根据需要修改：

- `baseUrl`: 后端 API 地址（默认：`http://localhost:8080/api/v1`）
- `token`: JWT Token（登录后自动填充）
- `sprintId`: Sprint ID（默认：`1`）

**修改方式**：
- 点击集合右侧的 `...` 菜单
- 选择 **Edit**
- 切换到 **Variables** 标签页
- 修改 `Current value` 列的值

### 2. 执行测试流程

#### 步骤 1: 用户登录

运行 **"1. 用户登录 (获取 Token)"** 请求：

- **默认账号**：`admin` / `admin123`
- 登录成功后，JWT Token 会自动保存到集合变量 `{{token}}`
- 查看 Postman 控制台可以看到 "Token saved: xxx" 日志

**测试账号**（参考 `backend/init.sql`）：
```
用户名: admin     密码: admin123    (系统管理员)
用户名: manager   密码: manager123  (项目经理)
用户名: dev1      密码: dev123      (开发人员)
```

#### 步骤 2: 调用 Sprint 完成概率预测接口

运行 **"2. 获取 Sprint 完成概率预测"** 请求：

- 请求会自动携带 Authorization Header：`Bearer {{token}}`
- 默认查询 Sprint ID = 1
- 需要 `SPRINT:VIEW` 权限

**返回示例**：
```json
{
  "probability": 0.8523,
  "riskLevel": "LOW",
  "features": {
    "progressRate": 0.65,
    "velocityTrend": "STABLE",
    "taskCompletionRate": 0.72
  }
}
```

**字段说明**：
- `probability`: 完成概率（0-1 之间，0.8523 表示 85.23%）
- `riskLevel`: 风险等级
  - `LOW`: 低风险（概率 > 0.7）
  - `MEDIUM`: 中风险（0.5 < 概率 <= 0.7）
  - `HIGH`: 高风险（概率 <= 0.5）
- `features`: 特征摘要（具体字段由后端实现决定）

#### 步骤 3: 测试其他 Sprint

运行 **"3. 获取 Sprint 完成概率预测 (Sprint ID=2)"** 请求测试不同的 Sprint。

### 3. 可能的错误响应

#### 401 Unauthorized - Token 无效或过期
```json
{
  "timestamp": "2026-09-16T10:30:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "JWT token is expired or invalid"
}
```
**解决方法**：重新执行步骤 1 登录获取新 Token

#### 403 Forbidden - 权限不足
```json
{
  "timestamp": "2026-09-16T10:30:00",
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied"
}
```
**解决方法**：使用具有 `SPRINT:VIEW` 权限的账号登录

#### 404 Not Found - Sprint 不存在
```json
{
  "timestamp": "2026-09-16T10:30:00",
  "status": 404,
  "error": "Not Found",
  "message": "Sprint not found with id: 999"
}
```
**解决方法**：使用有效的 Sprint ID

#### 500 Internal Server Error - 预测服务异常
```json
{
  "timestamp": "2026-09-16T10:30:00",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Prediction service unavailable"
}
```
**可能原因**：
- Python ML 服务未启动
- 数据库连接失败
- Sprint 数据不完整

## 测试前准备

### 1. 启动后端服务

```bash
cd backend
mvn spring-boot:run
```

确认服务运行在 `http://localhost:8080`

### 2. 确认数据库已初始化

```bash
# 检查数据库中是否有 Sprint 数据
psql -U postgres -d burndown_db -c "SELECT id, name, status FROM sprints LIMIT 5;"
```

### 3. 检查 ML 预测服务（如果需要）

根据实现方式，可能需要启动独立的 Python ML 服务。

## 批量测试

可以使用 Postman 的 **Collection Runner** 批量执行测试：

1. 点击集合右侧的 `...` 菜单
2. 选择 **Run collection**
3. 选择要运行的请求（全选或部分）
4. 点击 **Run Burndown Management...** 按钮
5. 查看测试结果

## 故障排除

### Token 未自动保存

1. 检查登录响应是否包含 `token` 字段
2. 打开 Postman Console（View → Show Postman Console）查看脚本执行日志
3. 手动复制 token 并设置到集合变量

### 连接失败

1. 确认后端服务已启动：`curl http://localhost:8080/api/v1/actuator/health`
2. 检查防火墙设置
3. 验证 `baseUrl` 变量配置是否正确

### 权限错误

使用 `admin` 账号登录，该账号默认拥有所有权限。

## 扩展测试

可以基于此集合添加更多测试用例：

- 边界值测试（无效的 Sprint ID、负数、极大值）
- 并发测试（同时请求多个 Sprint）
- 性能测试（Collection Runner 设置迭代次数和延迟）
- 数据驱动测试（使用 CSV 文件导入多个 Sprint ID）

## 相关文档

- API 文档：`http://localhost:8080/api/v1/swagger-ui.html`
- 后端代码：`backend/src/main/java/com/burndown/controller/SprintPredictionController.java`
- 数据库初始化：`backend/init.sql`
