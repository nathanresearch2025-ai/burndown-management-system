# Nginx 反向代理配置说明

## 问题描述

前端应用在 `http://159.75.202.106:30173`，后端 API 在 `http://159.75.202.106:30080`。

前端代码中使用相对路径 `/api/v1`，导致请求发送到前端服务器而不是后端服务器，造成注册等 API 调用失败。

## 解决方案

在前端的 Nginx 配置中添加反向代理，将所有 `/api/v1` 开头的请求转发到后端服务器。

## 配置详情

### Nginx 配置

```nginx
server {
    listen 80;

    # 前端静态文件
    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # API 反向代理到后端
    location /api/v1 {
        proxy_pass http://159.75.202.106:30080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # 超时设置
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }
}
```

### 配置说明

1. **location /**
   - 处理前端静态文件请求
   - 使用 `try_files` 支持 React Router 的前端路由

2. **location /api/v1**
   - 将所有 API 请求代理到后端服务器
   - `proxy_pass`: 后端服务器地址
   - `proxy_set_header`: 传递原始请求头信息
   - 超时设置：60秒（可根据需要调整）

## 请求流程

### 之前（失败）
```
浏览器 → http://159.75.202.106:30173/api/v1/auth/register
       → 前端 Nginx (没有该路径) → 404 错误
```

### 现在（成功）
```
浏览器 → http://159.75.202.106:30173/api/v1/auth/register
       → 前端 Nginx (反向代理)
       → http://159.75.202.106:30080/api/v1/auth/register
       → 后端服务器 → 返回结果
```

## 验证

### 测试注册接口
```bash
curl -X POST http://159.75.202.106:30173/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","email":"test@example.com","password":"Test123456","fullName":"Test User"}'
```

**预期结果**: 返回 HTTP 200 和 token

### 查看 Nginx 配置
```bash
kubectl exec $(kubectl get pod -l app=burndown-frontend -o jsonpath='{.items[0].metadata.name}') \
  -- cat /etc/nginx/conf.d/default.conf
```

## 优势

1. **前端代码无需修改**: 继续使用相对路径 `/api/v1`
2. **避免跨域问题**: 前端和 API 在同一域名下
3. **生产环境最佳实践**: 符合标准的反向代理架构
4. **统一入口**: 所有请求通过前端服务器，便于管理

## 注意事项

1. **后端地址硬编码**: 当前配置中后端地址是硬编码的 `http://159.75.202.106:30080`
   - 如果后端地址变化，需要重新构建前端镜像
   - 生产环境建议使用环境变量或配置文件

2. **超时设置**: 当前设置为 60 秒
   - 如果有长时间运行的 API，可能需要增加超时时间
   - 可以针对特定路径设置不同的超时

3. **缓存**: 当前未配置 API 响应缓存
   - 如需缓存，可以添加 `proxy_cache` 相关配置

## 相关文件

- `/myapp/frontend/Dockerfile` - 包含 Nginx 配置的 Dockerfile
- `/myapp/frontend/src/api/axios.ts` - 前端 API 配置（使用相对路径）
- `/myapp/deploy.sh` - 部署脚本（自动构建和部署）

## 部署

配置已集成到部署脚本中，运行以下命令即可：

```bash
./deploy.sh
```

脚本会自动：
1. 构建包含新 Nginx 配置的前端镜像
2. 导入镜像到 K3s
3. 重新部署前端服务

## 故障排查

### 问题：API 请求仍然失败

**检查步骤**:
1. 确认前端 Pod 已重启并使用新镜像
   ```bash
   kubectl get pods -l app=burndown-frontend
   ```

2. 查看 Nginx 配置是否正确
   ```bash
   kubectl exec <frontend-pod> -- cat /etc/nginx/conf.d/default.conf
   ```

3. 查看 Nginx 日志
   ```bash
   kubectl logs -l app=burndown-frontend
   ```

4. 测试后端服务是否可访问
   ```bash
   curl http://159.75.202.106:30080/api/v1/auth/register
   ```

### 问题：502 Bad Gateway

**可能原因**:
- 后端服务未启动或不可访问
- 后端地址配置错误
- 网络连接问题

**解决方法**:
1. 检查后端 Pod 状态
   ```bash
   kubectl get pods -l app=burndown-backend
   ```

2. 测试后端连接
   ```bash
   kubectl exec <frontend-pod> -- wget -O- http://159.75.202.106:30080/api/v1/auth/register
   ```

### 问题：504 Gateway Timeout

**可能原因**:
- API 响应时间超过 60 秒
- 后端服务响应慢

**解决方法**:
- 增加超时时间（修改 Dockerfile 中的 `proxy_*_timeout` 配置）
- 优化后端性能
