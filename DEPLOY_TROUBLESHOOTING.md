# 部署问题分析和解决方案

## 问题根源

### 问题 1: Docker 构建缓存
**现象**: 即使代码更新，Docker 构建使用缓存（CACHED），导致镜像包含旧代码。

**解决方案**:
- 在 deploy.sh 中默认使用 `--no-cache` 标志强制重新构建
- 添加 `--cache` 参数支持快速构建（适合代码未变化时）

### 问题 2: K3s 使用 containerd 而非 Docker
**现象**:
- Docker 构建的镜像存储在 Docker 中
- Kubernetes (K3s) 使用 containerd 作为容器运行时
- 两者的镜像存储是独立的，互不可见

**关键发现**:
```bash
# Docker 镜像
docker images burndown-frontend:latest
# ID: 4beeff510d5f

# K3s containerd 镜像（旧的）
k3s crictl images | grep burndown-frontend
# ID: 7b6ec0ccadc6a
```

**解决方案**:
使用 `k3s ctr images import` 将 Docker 镜像导入到 K3s 的 containerd：
```bash
k3s ctr images import /dev/stdin < <(docker save burndown-frontend:latest)
```

### 问题 3: imagePullPolicy 配置
**现象**:
- 原配置使用 `imagePullPolicy: Never`
- 导致 K3s 只能使用本地已存在的镜像
- 如果镜像不在 containerd 中，Pod 启动失败

**解决方案**:
- 将 imagePullPolicy 改为 `IfNotPresent`
- 允许 K3s 在本地找不到镜像时尝试拉取
- 对于本地镜像，仍然优先使用本地版本

### 问题 4: hostNetwork 端口冲突
**现象**:
- 使用 `hostNetwork: true` 时，Pod 直接使用主机网络
- 滚动更新时，新旧 Pod 同时存在会导致端口冲突
- 新 Pod 无法启动（Pending 状态）

**解决方案**:
- 删除旧部署后等待 Pod 完全终止
- 再创建新部署，避免端口冲突

## 最终解决方案

### 1. 更新 deploy.sh 脚本

**关键改动**:
```bash
# 构建镜像后立即导入到 K3s
docker build --no-cache -t burndown-frontend:latest .
k3s ctr images import /dev/stdin < <(docker save burndown-frontend:latest)
```

### 2. 更新 K8s 配置文件

**frontend.yaml 和 backend.yaml**:
```yaml
imagePullPolicy: IfNotPresent  # 从 Never 改为 IfNotPresent
```

### 3. 部署流程

1. 使用 `--no-cache` 构建 Docker 镜像
2. 将镜像导入到 K3s containerd
3. 删除旧的 K8s 部署
4. 等待 Pod 完全终止
5. 创建新部署
6. 导入数据库初始化脚本

## 验证新代码生效

### Frontend 验证
```bash
# 查看容器内的文件
kubectl exec $(kubectl get pod -l app=burndown-frontend -o jsonpath='{.items[0].metadata.name}') \
  -- cat /usr/share/nginx/html/index.html | grep "index-"

# 旧代码: index-Bw7GfJvV.js
# 新代码: index-C5hSecqO.js ✓
```

### Backend 验证
```bash
# 查看镜像 ID
k3s crictl images | grep burndown-backend

# 旧镜像: db51ea8d4413b
# 新镜像: 0f90623a527ff ✓
```

### 代码差异验证
新版本包含的更新：
- `TaskBoard.tsx` 中的 `handleCreate` 函数更新
- `SecurityConfig.java` 的 CORS 配置更新
- `BurndownPointRepository.java` 添加 @Modifying 注解

## 使用说明

### 正常部署（推荐）
```bash
./deploy.sh
```
- 强制重新构建镜像（不使用缓存）
- 自动导入到 K3s containerd
- 确保使用最新代码

### 快速部署
```bash
./deploy.sh --cache
```
- 使用 Docker 缓存加速构建
- 适合代码未变化时使用

## 关键命令

### 查看镜像
```bash
# Docker 镜像
docker images | grep burndown

# K3s containerd 镜像
k3s crictl images | grep burndown
```

### 手动导入镜像
```bash
# Frontend
k3s ctr images import /dev/stdin < <(docker save burndown-frontend:latest)

# Backend
k3s ctr images import /dev/stdin < <(docker save burndown-backend:latest)
```

### 强制重启 Pod
```bash
# 删除 Pod，让 Deployment 自动重建
kubectl delete pod -l app=burndown-frontend
kubectl delete pod -l app=burndown-backend
```

## 注意事项

1. **K3s 环境**: 本项目运行在 K3s 上，使用 containerd 作为容器运行时
2. **镜像导入**: 每次构建新镜像后必须导入到 K3s
3. **端口冲突**: 使用 hostNetwork 时注意端口冲突问题
4. **镜像策略**: 使用 IfNotPresent 而非 Never，提高灵活性

## 相关文件

- `/myapp/deploy.sh` - 主部署脚本（已更新）
- `/myapp/k8s/frontend.yaml` - Frontend 配置（imagePullPolicy 已更新）
- `/myapp/k8s/backend.yaml` - Backend 配置（imagePullPolicy 已更新）
- `/myapp/DEPLOY.md` - 部署说明文档
- `/myapp/DEPLOY_TROUBLESHOOTING.md` - 本文档（问题排查）
