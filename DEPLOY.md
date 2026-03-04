# 部署说明文档

## 问题分析

### 发现的问题
在之前的部署中，Docker 构建使用了缓存（CACHED），导致即使代码更新了，镜像仍然使用旧代码。

### 代码差异
新版本代码与备份代码的主要差异：
- `SecurityConfig.java`: CORS 配置从 `setAllowedOriginPatterns("*")` 改为 `setAllowedOrigins(...)`
- `BurndownPointRepository.java`: 添加了 `@Modifying` 注解和新方法
- `WorkLogRepository.java`: 相关更新

## 解决方案

### 更新内容
已更新 `deploy.sh` 脚本，添加以下功能：

1. **默认强制重新构建**
   - 使用 `--no-cache` 标志确保每次都使用最新代码
   - 避免 Docker 缓存导致的代码不生效问题

2. **可选缓存模式**
   - 添加 `--cache` 参数支持快速构建
   - 适合代码未变化时使用，节省构建时间

3. **清晰的使用说明**
   - 脚本头部添加使用方法注释
   - 部署完成后显示使用说明

## 使用方法

### 基本部署（推荐）
```bash
./deploy.sh
```
- 强制重新构建所有镜像（不使用缓存）
- 确保使用最新代码
- 适合代码有更新时使用

### 快速部署
```bash
./deploy.sh --cache
```
- 使用 Docker 缓存加速构建
- 适合代码未变化，仅需重新部署时使用
- 构建速度更快

## 部署流程

脚本执行的 7 个步骤：

1. **构建 Backend Docker 镜像**
   - 使用 Maven 构建 Spring Boot 应用
   - 打包成 JAR 文件
   - 创建运行时镜像

2. **构建 Frontend Docker 镜像**
   - 使用 Node.js 构建 React 应用
   - 生成静态文件
   - 使用 Nginx 提供服务

3. **删除旧的 K8s 部署**
   - 删除现有的 backend、frontend、postgres 部署
   - 等待 Pod 完全终止

4. **部署 PostgreSQL**
   - 创建数据库 Deployment 和 Service
   - 等待 PostgreSQL 就绪

5. **导入数据库初始化脚本**
   - 复制 init.sql 到 PostgreSQL Pod
   - 执行 SQL 脚本创建表结构
   - 导入示例数据

6. **部署 Backend**
   - 创建 Backend Deployment 和 Service
   - 等待 Backend 就绪

7. **部署 Frontend**
   - 创建 Frontend Deployment 和 Service
   - 等待 Frontend 就绪

## 验证部署

### 查看 Pod 状态
```bash
kubectl get pods
```

### 查看服务状态
```bash
kubectl get services
```

### 查看日志
```bash
# Backend 日志
kubectl logs -l app=burndown-backend

# Frontend 日志
kubectl logs -l app=burndown-frontend

# PostgreSQL 日志
kubectl logs -l app=postgres
```

### 访问应用
- Frontend: http://<节点IP>:30173
- Backend API: http://<节点IP>:30080
- PostgreSQL: <节点IP>:30432

## 镜像信息

部署后的镜像：
- `burndown-backend:latest` - Spring Boot 后端应用
- `burndown-frontend:latest` - React 前端应用
- `postgres:15` - PostgreSQL 数据库

## 注意事项

1. **首次部署或代码更新**
   - 必须使用 `./deploy.sh`（不带参数）
   - 确保使用最新代码构建镜像

2. **重新部署（代码未变化）**
   - 可以使用 `./deploy.sh --cache` 加速
   - 适合配置变化或 K8s 资源调整

3. **数据持久化**
   - 当前 PostgreSQL 使用 emptyDir 存储
   - Pod 删除后数据会丢失
   - 生产环境建议使用 PersistentVolume

4. **网络模式**
   - 当前使用 hostNetwork: true
   - 所有服务直接使用主机网络
   - 注意端口冲突问题

## 故障排查

### 镜像构建失败
```bash
# 查看 Docker 日志
docker logs <container-id>

# 清理旧镜像
docker system prune -a
```

### Pod 启动失败
```bash
# 查看 Pod 详情
kubectl describe pod <pod-name>

# 查看 Pod 日志
kubectl logs <pod-name>
```

### 数据库连接失败
```bash
# 检查 PostgreSQL 是否就绪
kubectl get pod -l app=postgres

# 测试数据库连接
kubectl exec -it <postgres-pod> -- psql -U postgres -d burndown_db
```

## 相关文件

- `/myapp/deploy.sh` - 主部署脚本
- `/myapp/backup_and_restore.sh` - 备份和恢复脚本
- `/myapp/k8s/` - Kubernetes 配置文件目录
  - `backend.yaml` - Backend 部署配置
  - `frontend.yaml` - Frontend 部署配置
  - `postgres.yaml` - PostgreSQL 部署配置
- `/myapp/backend/Dockerfile` - Backend 镜像构建文件
- `/myapp/frontend/Dockerfile` - Frontend 镜像构建文件
- `/myapp/backend/init.sql` - 数据库初始化脚本
