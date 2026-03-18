# DJL 本地向量生成 Postman 测试指南

## 📦 文件说明

本目录包含 DJL 本地向量生成功能的完整 Postman 测试集合：

### 测试集合文件
- **DJL_Embedding_Tests.postman_collection.json** - 完整测试集合

### 环境配置文件
- **DJL_Embedding_Local.postman_environment.json** - 本地开发环境
- **DJL_Embedding_Production.postman_environment.json** - 生产环境

## 🚀 快速开始

### 1. 导入到 Postman

#### 方式一：通过 Postman 应用导入
1. 打开 Postman 应用
2. 点击左上角 **Import** 按钮
3. 选择 **File** 标签
4. 拖拽或选择以下文件：
   - `DJL_Embedding_Tests.postman_collection.json`
   - `DJL_Embedding_Local.postman_environment.json`
   - `DJL_Embedding_Production.postman_environment.json`
5. 点击 **Import** 完成导入

#### 方式二：通过命令行导入（使用 Newman）
```bash
npm install -g newman
newman run DJL_Embedding_Tests.postman_collection.json \
  -e DJL_Embedding_Local.postman_environment.json
```

### 2. 选择环境

在 Postman 右上角的环境下拉菜单中选择：
- **DJL Embedding - Local Development** (本地测试)
- **DJL Embedding - Production** (生产环境测试)

### 3. 启动后端服务

```bash
cd backend
mvn spring-boot:run
```

确保服务运行在 `http://localhost:8080`

### 4. 运行测试

按顺序执行测试文件夹：
1. **Service Management** - 检查服务状态
2. **Basic Embedding Tests** - 基础功能测试
3. **Edge Cases** - 边界情况测试
4. **AI Task Generation** - AI 功能测试（需要先登录）
5. **Performance Tests** - 性能基准测试

## 📋 测试集合结构

### 1. Service Management（服务管理）
- **Get Embedding Provider Info** - 获取向量服务配置信息
  - 验证服务提供者类型（DJL/API）
  - 检查模型加载状态
  - 查看模型名称和引擎类型

### 2. Basic Embedding Tests（基础测试）
- **Test English Text Embedding** - 英文文本向量生成
- **Test Chinese Text Embedding** - 中文文本向量生成
- **Test Mixed Language Embedding** - 中英文混合文本
- **Test Long Text Embedding** - 长文本（包含任务元数据）
- **Test Short Text Embedding** - 极短文本

### 3. Edge Cases（边界情况）
- **Test Empty Text** - 空文本（应返回 400 错误）
- **Test Special Characters** - 特殊字符处理
- **Test Code Snippet** - 代码片段向量生成

### 4. AI Task Generation with Vector Search（AI 任务生成）
- **Login to Get Token** - 登录获取 JWT Token
- **Generate Task Description (Vector Search)** - 使用向量搜索生成任务描述
- **Generate Task Description - Feature** - 功能类型任务
- **Generate Task Description - Bug Fix** - Bug 修复任务

### 5. Performance Tests（性能测试）
- **Performance Test - Iteration 1-3** - 连续性能测试
- **Performance Test - Chinese Text** - 中文文本性能

## 🎯 测试用例详解

### 测试用例 1: 获取服务信息
```http
GET {{base_url}}/embeddings/info
```

**预期响应**：
```json
{
  "provider": "djl",
  "modelName": "sentence-transformers/all-MiniLM-L6-v2",
  "engine": "PyTorch",
  "status": "loaded"
}
```

**验证点**：
- ✅ 状态码 200
- ✅ provider 为 "djl"
- ✅ status 为 "loaded"

### 测试用例 2: 英文文本向量生成
```http
POST {{base_url}}/embeddings/test
Content-Type: application/json

{
  "text": "Implement user authentication feature with JWT token"
}
```

**预期响应**：
```json
{
  "success": true,
  "dimension": 384,
  "durationMs": 45,
  "provider": {
    "provider": "djl",
    "modelName": "sentence-transformers/all-MiniLM-L6-v2"
  }
}
```

**验证点**：
- ✅ 状态码 200
- ✅ success 为 true
- ✅ dimension 为 384
- ✅ durationMs < 200ms

### 测试用例 3: AI 任务描述生成
```http
POST {{base_url}}/tasks/ai/generate-description
Authorization: Bearer {{auth_token}}
Content-Type: application/json

{
  "projectId": 1,
  "title": "优化数据库查询性能",
  "type": "TASK",
  "priority": "HIGH",
  "storyPoints": 5
}
```

**预期响应**：
```json
{
  "description": "任务描述内容...",
  "similarTasks": [
    {
      "id": 1,
      "taskKey": "PROJ-123",
      "title": "相似任务标题",
      "similarity": 0.85
    }
  ],
  "generatedBy": "external-llm",
  "generatedAt": "2026-03-18T10:30:00"
}
```

**验证点**：
- ✅ 状态码 200
- ✅ 返回任务描述
- ✅ 返回相似任务列表
- ✅ 包含生成方式标识

## 📊 性能基准

### 预期性能指标（本地 DJL 模式）

| 测试场景 | 预期耗时 | 验证标准 |
|---------|---------|---------|
| 短文本（<20字） | 30-50ms | < 100ms |
| 中等文本（20-100字） | 40-80ms | < 150ms |
| 长文本（>100字） | 80-120ms | < 200ms |
| 中文文本 | 40-90ms | < 150ms |
| 代码片段 | 50-100ms | < 200ms |

### 性能对比（API vs DJL）

| 指标 | API 模式 | DJL 模式 | 提升 |
|------|---------|---------|------|
| 平均响应时间 | 300-500ms | 50-100ms | **3-5x** |
| 首次加载 | 0ms | 5-10秒 | - |
| 网络依赖 | 必需 | 不需要 | ✅ |
| 成本 | 按量计费 | 免费 | **100%** |

## 🔧 环境变量说明

### 本地开发环境
```json
{
  "base_url": "http://localhost:8080/api/v1",
  "username": "admin",
  "password": "admin123",
  "project_id": "1"
}
```

### 生产环境
```json
{
  "base_url": "http://159.75.202.106:30080/api/v1",
  "username": "admin",
  "password": "admin123",
  "project_id": "1"
}
```

## 🧪 自动化测试脚本

每个请求都包含自动化测试脚本，会验证：

1. **HTTP 状态码** - 确保请求成功
2. **响应结构** - 验证必需字段存在
3. **数据类型** - 检查字段类型正确
4. **业务逻辑** - 验证业务规则
5. **性能指标** - 记录响应时间

### 测试脚本示例

```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Embedding generated successfully", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.success).to.be.true;
    pm.expect(jsonData.dimension).to.eql(384);
});

pm.test("Response time is acceptable", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.durationMs).to.be.below(200);
});
```

## 📈 运行完整测试套件

### 使用 Postman Collection Runner

1. 点击集合名称旁的 **...** 按钮
2. 选择 **Run collection**
3. 选择环境：**DJL Embedding - Local Development**
4. 点击 **Run DJL Embedding Tests**
5. 查看测试结果和性能报告

### 使用 Newman（命令行）

```bash
# 安装 Newman
npm install -g newman newman-reporter-htmlextra

# 运行测试并生成 HTML 报告
newman run DJL_Embedding_Tests.postman_collection.json \
  -e DJL_Embedding_Local.postman_environment.json \
  -r htmlextra \
  --reporter-htmlextra-export ./test-report.html

# 运行测试并输出到控制台
newman run DJL_Embedding_Tests.postman_collection.json \
  -e DJL_Embedding_Local.postman_environment.json \
  --color on
```

## 🐛 故障排除

### 问题 1: 连接被拒绝
**错误**: `Error: connect ECONNREFUSED 127.0.0.1:8080`

**解决方案**:
1. 确认后端服务已启动
2. 检查端口是否正确（默认 8080）
3. 验证防火墙设置

### 问题 2: 401 未授权
**错误**: `Status code is 401`

**解决方案**:
1. 先运行 "Login to Get Token" 请求
2. 确认 token 已保存到环境变量
3. 检查 token 是否过期（默认 7 天）

### 问题 3: 向量维度不匹配
**错误**: `Expected dimension 384 but got 1536`

**解决方案**:
1. 检查配置中的 `ai.embedding.provider` 是否为 "djl"
2. 确认 DJL 模型已正确加载
3. 查看应用启动日志

### 问题 4: 性能测试超时
**错误**: `Response time exceeds 200ms`

**解决方案**:
1. 首次请求会较慢（模型预热）
2. 后续请求应该更快
3. 检查系统资源（CPU/内存）

## 📝 测试报告

运行完整测试套件后，Postman 会生成测试报告，包括：

- ✅ 通过的测试数量
- ❌ 失败的测试数量
- ⏱️ 平均响应时间
- 📊 性能趋势图
- 📋 详细的测试日志

### 示例测试报告

```
┌─────────────────────────┬──────────┬──────────┐
│                         │ executed │   failed │
├─────────────────────────┼──────────┼──────────┤
│              iterations │        1 │        0 │
├─────────────────────────┼──────────┼──────────┤
│                requests │       15 │        0 │
├─────────────────────────┼──────────┼──────────┤
│            test-scripts │       30 │        0 │
├─────────────────────────┼──────────┼──────────┤
│      prerequest-scripts │        5 │        0 │
├─────────────────────────┼──────────┼──────────┤
│              assertions │       45 │        0 │
├─────────────────────────┴──────────┴──────────┤
│ total run duration: 2.5s                      │
├───────────────────────────────────────────────┤
│ total data received: 15.2KB (approx)          │
├───────────────────────────────────────────────┤
│ average response time: 65ms                   │
└───────────────────────────────────────────────┘
```

## 🎓 最佳实践

1. **按顺序执行** - 某些测试依赖前面的测试结果（如登录）
2. **检查环境** - 确保选择了正确的环境配置
3. **查看日志** - 使用 Console 查看详细的测试输出
4. **性能基准** - 多次运行性能测试取平均值
5. **定期更新** - 随着功能更新及时更新测试用例

## 📚 相关文档

- [DJL 实现文档](../docs/ai-agent/DJL_LOCAL_EMBEDDING_IMPLEMENTATION.md)
- [快速开始指南](../docs/ai-agent/DJL_QUICK_START.md)
- [实现总结](../docs/ai-agent/DJL_IMPLEMENTATION_SUMMARY.md)

---

**测试愉快！** 🚀 如有问题，请查看故障排除部分或联系开发团队。
