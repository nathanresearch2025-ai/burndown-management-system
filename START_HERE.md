# 🚀 立即开始 - 3 步完成测试

## 第 1 步: 重启服务 ⚡

打开终端，执行：

```bash
cd D:\java\claude\projects\2\backend

# 如果服务正在运行，先停止 (Ctrl+C)

# 重新启动
mvn spring-boot:run
```

**等待看到**：
```
Started BurndownManagementApplication in X seconds
```

---

## 第 2 步: 验证服务 ✅

### 方式 A: 使用验证脚本（推荐）

```bash
# Windows
cd D:\java\claude\projects\2\backend
verify-service.bat

# Linux/Mac
cd backend
chmod +x verify-service.sh
./verify-service.sh
```

### 方式 B: 手动验证

```bash
# 1. 健康检查
curl http://localhost:8080/api/v1/actuator/health

# 2. 向量服务信息（验证 403 已修复）
curl http://localhost:8080/api/v1/embeddings/info

# 3. 测试向量生成
curl -X POST http://localhost:8080/api/v1/embeddings/test \
  -H "Content-Type: application/json" \
  -d '{"text": "测试"}'
```

**预期结果**：
- ✅ 所有请求返回 200 OK
- ✅ 没有 403 Forbidden 错误
- ✅ 向量生成成功

---

## 第 3 步: Postman 测试 📬

### 3.1 导入文件

1. 打开 Postman
2. 点击 **Import** 按钮
3. 拖拽以下文件到导入窗口：
   ```
   D:\java\claude\projects\2\postman\DJL_Embedding_Tests.postman_collection.json
   D:\java\claude\projects\2\postman\DJL_Embedding_Local.postman_environment.json
   ```

### 3.2 选择环境

在 Postman 右上角的下拉菜单中选择：
```
DJL Embedding - Local Development
```

### 3.3 运行测试

按顺序执行以下测试：

#### ① Service Management
- **Get Embedding Provider Info**
  - 预期：200 OK
  - 验证：provider 为 "api"

#### ② Basic Embedding Tests
- **Test English Text Embedding**
  - 预期：200 OK，dimension: 1536
- **Test Chinese Text Embedding**
  - 预期：200 OK，dimension: 1536
- **Test Mixed Language Embedding**
  - 预期：200 OK
- **Test Long Text Embedding**
  - 预期：200 OK，durationMs < 500
- **Test Short Text Embedding**
  - 预期：200 OK

#### ③ Edge Cases
- **Test Empty Text**
  - 预期：400 Bad Request（正常）
- **Test Special Characters**
  - 预期：200 OK
- **Test Code Snippet**
  - 预期：200 OK

#### ④ AI Task Generation
- **Login to Get Token**
  - 预期：200 OK，获取 token
- **Generate Task Description**
  - 预期：200 OK，返回任务描述和相似任务

#### ⑤ Performance Tests
- **Performance Test - Iteration 1-3**
  - 预期：200 OK，记录响应时间
- **Performance Test - Chinese Text**
  - 预期：200 OK

---

## 🎯 成功标准

### 全部通过的标志
- ✅ 17 个请求全部成功
- ✅ 0 个失败
- ✅ 平均响应时间 < 500ms
- ✅ 所有断言通过

### 测试报告示例
```
✓ Status code is 200
✓ Embedding generated successfully
✓ Response time is acceptable
✓ Provider is API

Tests:     51 passed
Requests:  17 passed
Duration:  8.5s
```

---

## ❌ 如果遇到问题

### 问题 1: 服务启动失败
**解决**：查看控制台错误日志，参考 [故障排除文档](./POSTMAN_TROUBLESHOOTING.md)

### 问题 2: 仍然返回 403
**解决**：确认已重启服务，SecurityConfig 修改才会生效

### 问题 3: 向量生成失败
**解决**：检查 DeepSeek API 密钥是否有效

### 问题 4: Postman 连接失败
**解决**：
1. 确认服务已启动
2. 检查 URL 是否正确：`http://localhost:8080/api/v1`
3. 确认已选择正确的环境

---

## 📊 完成后的状态

### 功能状态
- 🟢 向量服务 API - 正常工作
- 🟢 向量生成 - 正常工作
- 🟢 AI 任务生成 - 正常工作
- 🟢 相似度搜索 - 正常工作

### 性能指标
- 启动时间：10-15 秒
- 响应时间：200-400ms
- 向量维度：1536
- 成功率：100%

---

## 📚 相关文档

- [完整故障排除](./POSTMAN_TROUBLESHOOTING.md)
- [最终总结](./FINAL_SUMMARY.md)
- [Postman 测试指南](../../postman/README_DJL_TESTS.md)

---

## ✨ 下一步

测试通过后，你可以：

1. **集成到前端** - 使用向量服务 API
2. **批量生成向量** - 为现有任务生成向量
3. **性能优化** - 根据实际使用情况调优
4. **切换到 DJL** - 解决 PyTorch 问题后切换到本地模式

---

**现在开始第 1 步：重启服务！** 🚀
