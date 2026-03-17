# LangChain 站会查询接口 Postman 测试指南

## 文件说明

### Collection 文件
- **Burndown_Langchain_Standup_Query.postman_collection.json** - 测试用例集合

### Environment 文件
- **Burndown_Langchain_Standup_Local.postman_environment.json** - 本地环境配置
- **Burndown_Langchain_Standup_Dev.postman_environment.json** - 开发服务器环境配置

## 快速开始

### 1. 导入到 Postman

#### 导入 Collection
1. 打开 Postman
2. 点击左上角 "Import" 按钮
3. 选择 `Burndown_Langchain_Standup_Query.postman_collection.json`
4. 点击 "Import"

#### 导入 Environment
1. 点击右上角齿轮图标（Manage Environments）
2. 点击 "Import" 按钮
3. 选择环境文件（Local 或 Dev）
4. 点击 "Import"

### 2. 选择环境

在 Postman 右上角的环境下拉菜单中选择对应环境。

### 3. 运行测试

选择任意请求，点击 "Send" 按钮即可测试。

## 环境变量

| 变量名 | Local 默认值 | Dev 默认值 | 说明 |
|--------|-------------|-----------|------|
| baseUrl | http://localhost:8080/api/v1 | http://159.75.202.106:30080/api/v1 | Spring Boot 后端 |
| langchainUrl | http://localhost:8091 | http://159.75.202.106:30091 | LangChain Python 服务 |
| projectId | 1 | 1 | 项目 ID |
| sprintId | 1 | 1 | Sprint ID |
| userId | 1 | 1 | 用户 ID |

## 性能测试

1. 选择 "性能测试 - 单次请求"
2. 点击 "..." → "Run"
3. 设置 Iterations 为 50-100
4. 运行后查看 Console 中的统计数据（P50, P95, P99）

## 更多信息

详细文档请查看：
- `docs/PERFORMANCE_OPTIMIZATION_SUMMARY.md`
- `docs/LOGGING_ENHANCEMENT.md`
