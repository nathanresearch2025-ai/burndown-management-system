# 压力测试失败分析报告

## 执行摘要

**测试时间**: 2026-03-04 00:00 - 00:48
**失败版本**: v1, v2, v3
**失败率**: 100% (所有登录请求失败)
**根本原因**: 后端服务器内部错误 (HTTP 500)

---

## 问题概述

从 2026-03-04 开始的三次压力测试全部失败，所有登录请求返回 HTTP 500 错误。对比之前成功的测试（2026-03-03），问题出现在后端服务，而非测试脚本。

### 失败测试对比

| 版本 | 时间 | 总请求数 | 成功率 | 状态 |
|------|------|---------|--------|------|
| 20260304-000044-v1 | 00:00 | 4,346 | 0.00% | ❌ 失败 |
| 20260304-000522-v2 | 00:05 | 4,492 | 50.00% | ⚠️ 部分失败 |
| 20260304-004547-v3 | 00:45 | 4,348 | 0.00% | ❌ 失败 |

### 成功测试对比（参考基准）

| 版本 | 时间 | 总请求数 | 成功率 | 状态 |
|------|------|---------|--------|------|
| 20260303-231129-v1 | 23:11 | 7,398 | 100.00% | ✅ 成功 |
| 20260303-231955-v2 | 23:19 | 7,329 | 100.00% | ✅ 成功 |

---

## 详细失败分析

### 1. 登录接口失败 (最新测试 v3)

```json
{
  "scenario": "baseline",
  "stats": {
    "login": {
      "total": 148,
      "success": 0,
      "fail": 148,
      "success_rate": 0.0
    }
  }
}
```

**所有三个场景的登录请求 100% 失败**:
- Baseline: 148/148 失败
- Standard: 1,200/1,200 失败
- Peak: 3,000/3,000 失败

### 2. 后续接口未执行

由于登录失败，测试脚本无法获取认证 token，导致：
- Projects 接口: 0 次请求
- Tasks 接口: 0 次请求

这是**级联失败**，不是这些接口本身的问题。

### 3. 错误响应详情

**直接测试登录接口返回**:
```bash
$ curl -X POST http://159.75.202.106:30080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser1","password":"Test123456"}'

HTTP/1.1 500 Internal Server Error
{
  "code": "INTERNAL_ERROR",
  "data": null,
  "message": "服务器内部错误"
}
```

---

## 根本原因分析

### 后端服务状态

✅ **后端 Pod 运行正常**:
```
NAME                               READY   STATUS    RESTARTS   AGE
burndown-backend-59bd484bd-qknfd   1/1     Running   0          6m47s
```

✅ **数据库 Pod 运行正常**:
```
postgres-69cc7cdd46-tbbwx          1/1     Running   0          7m14s
```

✅ **测试用户存在于数据库**:
```sql
SELECT username, email FROM users WHERE username='testuser1';
 username  |         email
-----------+-----------------------
 testuser1 | testuser1@example.com
```

### 可能的原因

基于以上信息，问题可能是：

1. **密码加密/验证问题**
   - 数据库中的密码哈希可能损坏或格式不正确
   - BCrypt 验证逻辑可能出现异常

2. **JWT 生成问题**
   - JWT 密钥配置问题
   - Token 生成过程中的异常

3. **数据库连接池问题**
   - 高并发下连接池耗尽
   - 连接超时或死锁

4. **最近的代码变更**
   - 2026-03-03 23:19 之后到 2026-03-04 00:00 之间可能有部署更新
   - 配置文件变更

5. **Redis 连接问题**
   - Redis 服务不可用
   - Session 存储失败

---

## 排查步骤

### 1. 检查后端日志（高优先级）

```bash
# 查看详细错误堆栈
kubectl logs -l app=burndown-backend --tail=500 | grep -A 20 "Exception"

# 查看登录相关日志
kubectl logs -l app=burndown-backend --tail=500 | grep -i "login\|auth"

# 实时监控日志
kubectl logs -f -l app=burndown-backend
```

### 2. 验证数据库连接

```bash
# 检查数据库连接
kubectl exec -it postgres-69cc7cdd46-tbbwx -- psql -U postgres -d burndown_db

# 验证用户密码哈希
SELECT username, password FROM users WHERE username='testuser1';

# 检查数据库连接数
SELECT count(*) FROM pg_stat_activity;
```

### 3. 检查 Redis 状态

```bash
# 查找 Redis Pod
kubectl get pods | grep redis

# 如果 Redis 存在，检查连接
kubectl exec -it <redis-pod> -- redis-cli ping
```

### 4. 检查应用配置

```bash
# 查看 ConfigMap
kubectl get configmap

# 查看 Secret
kubectl get secret

# 检查环境变量
kubectl exec -it <backend-pod> -- env | grep -i "jwt\|database\|redis"
```

### 5. 测试密码验证

```bash
# 进入后端容器
kubectl exec -it <backend-pod> -- /bin/bash

# 或者查看应用配置文件
kubectl exec -it <backend-pod> -- cat /app/application.yml
```

---

## 建议的修复方案

### 方案 1: 重新初始化数据库（快速修复）

```bash
# 1. 重新运行数据库初始化脚本
kubectl exec -it postgres-69cc7cdd46-tbbwx -- psql -U postgres -d burndown_db -f /docker-entrypoint-initdb.d/init.sql

# 2. 或者删除并重新部署
kubectl delete pod postgres-69cc7cdd46-tbbwx
./deploy.sh
```

### 方案 2: 重启后端服务

```bash
# 重启后端 Pod
kubectl delete pod -l app=burndown-backend

# 等待新 Pod 启动
kubectl get pods -w
```

### 方案 3: 完全重新部署

```bash
# 清理所有资源
kubectl delete deployment burndown-backend
kubectl delete deployment postgres
kubectl delete service burndown-backend
kubectl delete service postgres

# 重新部署（不使用缓存）
./deploy.sh
```

### 方案 4: 检查并修复代码

如果是代码问题，需要：

1. 检查 `AuthController.java` 和 `AuthService.java`
2. 验证密码加密逻辑
3. 检查 JWT 生成代码
4. 添加详细的错误日志
5. 重新构建并部署

---

## 预防措施

### 1. 添加健康检查

在 `application.yml` 中配置：
```yaml
management:
  health:
    db:
      enabled: true
    redis:
      enabled: true
```

### 2. 增强错误日志

在登录接口添加详细日志：
```java
@PostMapping("/login")
public ResponseEntity<?> login(@RequestBody LoginRequest request) {
    try {
        log.info("Login attempt for user: {}", request.getUsername());
        // ... 登录逻辑
    } catch (Exception e) {
        log.error("Login failed for user: {}, error: {}",
                  request.getUsername(), e.getMessage(), e);
        throw e;
    }
}
```

### 3. 添加监控告警

配置 Prometheus 告警规则：
```yaml
- alert: HighLoginFailureRate
  expr: rate(http_requests_total{endpoint="/auth/login",status="500"}[5m]) > 0.1
  annotations:
    summary: "登录接口错误率过高"
```

### 4. 数据库备份

定期备份数据库：
```bash
kubectl exec -it postgres-pod -- pg_dump -U postgres burndown_db > backup.sql
```

---

## 测试脚本验证

测试脚本本身**没有问题**，验证如下：

✅ 配置文件正确:
```json
{
  "baseUrl": "http://159.75.202.106:30080/api/v1",
  "testData": {
    "users": [
      {
        "username": "testuser1",
        "password": "Test123456"
      }
    ]
  }
}
```

✅ 登录逻辑正确:
```python
def test_login(base_url, username, password):
    response = requests.post(
        f"{base_url}/auth/login",
        json={"username": username, "password": password},
        timeout=10
    )
    if response.status_code == 200:
        return response.json().get('token')
    return None
```

✅ 之前的测试成功证明脚本可用（2026-03-03 的测试 100% 成功）

---

## 时间线

| 时间 | 事件 | 状态 |
|------|------|------|
| 2026-03-02 19:13 | v5 测试 | ✅ 100% 成功 |
| 2026-03-03 23:11 | v1 测试 | ✅ 100% 成功 |
| 2026-03-03 23:19 | v2 测试 | ✅ 100% 成功 |
| **2026-03-04 00:00** | **v1 测试** | **❌ 0% 成功** |
| 2026-03-04 00:05 | v2 测试 | ⚠️ 50% 成功 |
| 2026-03-04 00:45 | v3 测试 | ❌ 0% 成功 |

**关键时间窗口**: 2026-03-03 23:19 - 2026-03-04 00:00（41分钟）

在这个时间窗口内可能发生了：
- 代码部署
- 配置变更
- 数据库维护
- 服务重启

---

## 结论

1. **问题定位**: 后端服务器内部错误，非测试脚本问题
2. **影响范围**: 所有登录请求失败，导致完整测试流程无法执行
3. **紧急程度**: 高 - 影响所有用户登录功能
4. **下一步**: 按照排查步骤检查后端日志，定位具体异常原因

---

## 附录

### 完整测试结果对比

**失败测试 (20260304-004547-v3)**:
```json
{
  "scenario": "baseline",
  "total_requests": 148,
  "total_success": 0,
  "success_rate": 0.0,
  "rps": 4.74
}
```

**成功测试 (20260303-231129-v1)**:
```json
{
  "scenario": "baseline",
  "total_requests": 996,
  "total_success": 996,
  "success_rate": 100.0,
  "rps": 32.42
}
```

### 相关文件

- 测试配置: `/myapp/test/all-interface-test/config.json`
- 测试脚本: `/myapp/test/pressure/scenario_pressure_test.py`
- 测试指南: `/myapp/test/pressure/SCENARIO_TEST_GUIDE_V2.md`
- 汇总报告: `/myapp/test/pressure/summary_report.html`
- 后端代码: `/myapp/backend/src/main/java/com/burndown/`
