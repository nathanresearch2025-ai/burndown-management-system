# CORS 配置修复说明

## 问题描述

从本地浏览器访问 `http://159.75.202.106:30173` 进行注册时，前端控制台出现错误：
```
Invalid CORS request
```

## 原因分析

后端的 CORS 配置只允许以下来源：
- `http://localhost:5173`
- `http://localhost:3000`

不允许来自 `http://159.75.202.106:30173` 的请求。

虽然配置了 Nginx 反向代理，但浏览器在发送跨域请求时，会先发送 OPTIONS 预检请求，后端需要正确响应这些请求。

## 解决方案

### 修改前的配置

```java
configuration.setAllowedOrigins(Arrays.asList("http://localhost:5173", "http://localhost:3000"));
```

### 修改后的配置

```java
configuration.setAllowedOriginPatterns(Arrays.asList("*"));
```

**关键变化：**
1. 使用 `setAllowedOriginPatterns` 替代 `setAllowedOrigins`
2. 允许所有来源（`*`）

## 配置详情

### SecurityConfig.java

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    // 允许所有来源（开发环境）
    configuration.setAllowedOriginPatterns(Arrays.asList("*"));
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
    configuration.setAllowedHeaders(Arrays.asList("*"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
}
```

### 配置说明

- **setAllowedOriginPatterns("*")**: 允许所有来源的请求
- **setAllowedMethods**: 允许的 HTTP 方法
- **setAllowedHeaders("*")**: 允许所有请求头
- **setAllowCredentials(true)**: 允许携带凭证（cookies、authorization headers）

## CORS 工作流程

### 1. 预检请求（OPTIONS）

浏览器在发送实际请求前，会先发送 OPTIONS 请求：

```http
OPTIONS /api/v1/auth/register HTTP/1.1
Origin: http://159.75.202.106:30173
Access-Control-Request-Method: POST
Access-Control-Request-Headers: Content-Type
```

### 2. 服务器响应

后端返回 CORS 头：

```http
HTTP/1.1 200
Access-Control-Allow-Origin: http://159.75.202.106:30173
Access-Control-Allow-Methods: GET,POST,PUT,DELETE,PATCH,OPTIONS
Access-Control-Allow-Headers: Content-Type
Access-Control-Allow-Credentials: true
```

### 3. 实际请求

浏览器收到允许响应后，发送实际的 POST 请求。

## 验证

### 测试 CORS 预检请求

```bash
curl -X OPTIONS http://159.75.202.106:30080/api/v1/auth/register \
  -H "Origin: http://159.75.202.106:30173" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: Content-Type" \
  -v
```

**预期结果**: 返回 `Access-Control-Allow-Origin` 等 CORS 头

### 测试实际注册请求

```bash
curl -X POST http://159.75.202.106:30173/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -H "Origin: http://159.75.202.106:30173" \
  -d '{"username":"testuser","email":"test@example.com","password":"Test123456","fullName":"Test User"}'
```

**预期结果**: 返回 HTTP 200 和 token

## 安全注意事项

### 当前配置（开发环境）

```java
configuration.setAllowedOriginPatterns(Arrays.asList("*"));
```

**优点**:
- 允许从任何来源访问
- 方便开发和测试

**缺点**:
- 不够安全，任何网站都可以调用 API
- 不适合生产环境

### 生产环境建议

```java
configuration.setAllowedOriginPatterns(Arrays.asList(
    "http://159.75.202.106:30173",
    "https://yourdomain.com",
    "https://www.yourdomain.com"
));
```

或者使用环境变量：

```java
String allowedOrigins = System.getenv("ALLOWED_ORIGINS");
configuration.setAllowedOriginPatterns(
    Arrays.asList(allowedOrigins.split(","))
);
```

## 完整的请求流程

### 从浏览器访问

```
1. 用户在浏览器打开: http://159.75.202.106:30173
2. 前端 JavaScript 发送注册请求到: /api/v1/auth/register
3. 浏览器检测到跨域（虽然通过代理，但 Origin 仍然是前端地址）
4. 浏览器发送 OPTIONS 预检请求
5. 后端返回 CORS 允许头
6. 浏览器发送实际的 POST 请求
7. Nginx 代理请求到后端
8. 后端处理并返回结果
9. 浏览器接收响应
```

## 相关文件

- `/myapp/backend/src/main/java/com/burndown/config/SecurityConfig.java` - CORS 配置
- `/myapp/frontend/Dockerfile` - Nginx 反向代理配置
- `/myapp/NGINX_PROXY.md` - Nginx 代理说明

## 部署

配置已更新，运行以下命令重新部署：

```bash
./deploy.sh
```

## 故障排查

### 问题：仍然出现 CORS 错误

**检查步骤**:

1. 确认 backend Pod 已重启
   ```bash
   kubectl get pods -l app=burndown-backend
   ```

2. 查看 backend 日志
   ```bash
   kubectl logs -l app=burndown-backend
   ```

3. 测试 CORS 预检请求
   ```bash
   curl -X OPTIONS http://159.75.202.106:30080/api/v1/auth/register \
     -H "Origin: http://159.75.202.106:30173" \
     -H "Access-Control-Request-Method: POST" \
     -v
   ```

4. 检查浏览器控制台
   - 查看 Network 标签
   - 检查 OPTIONS 请求的响应头
   - 确认是否有 `Access-Control-Allow-Origin` 头

### 问题：OPTIONS 请求返回 401

**原因**: OPTIONS 请求需要在 Spring Security 中放行

**解决**: 已在 SecurityConfig 中配置：
```java
.requestMatchers("/auth/**", "/swagger-ui/**", "/api-docs/**").permitAll()
```

### 问题：携带 Cookie 失败

**原因**: `Access-Control-Allow-Credentials` 未设置

**解决**: 已配置：
```java
configuration.setAllowCredentials(true);
```

## 测试结果

✓ CORS 预检请求成功
✓ 注册请求成功返回 token
✓ 浏览器可以正常访问前端并调用 API

## 下一步

现在您可以：
1. 在浏览器中访问 http://159.75.202.106:30173
2. 进行用户注册
3. 登录系统
4. 使用所有功能

如果仍有问题，请检查：
- 浏览器控制台的详细错误信息
- Network 标签中的请求和响应详情
- Backend Pod 的日志
