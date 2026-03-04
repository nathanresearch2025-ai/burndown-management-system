#!/bin/bash

# Kubernetes 部署脚本 - 仅部署 Backend、PostgreSQL 和 Redis
# 功能：构建 Backend Docker 镜像并部署到 K8s 集群
# 使用方法：
#   ./deploy-backend-only.sh                    # 强制重新构建（不使用缓存）
#   ./deploy-backend-only.sh --cache            # 使用缓存构建（更快）

set -e  # 遇到错误立即退出

# 配置变量
EXTERNAL_IP="159.75.202.106"  # 外网 IP 地址
BACKEND_PORT="30080"           # Backend NodePort
POSTGRES_PORT="30432"          # PostgreSQL NodePort
REDIS_PORT="30379"             # Redis NodePort
BUILD_TIMEOUT=1800             # 构建超时时间（秒），默认30分钟

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 工作目录
WORK_DIR="/myapp"
K8S_DIR="${WORK_DIR}/k8s"

# 检查部署模式
USE_CACHE=false
BUILD_FLAG="--no-cache"

if [ "$1" == "--cache" ]; then
    USE_CACHE=true
    BUILD_FLAG=""
    echo "使用缓存模式构建"
else
    echo "强制重新构建模式（不使用缓存）"
fi

echo "=========================================="
echo "开始部署 Backend、PostgreSQL 和 Redis 到 Kubernetes 集群"
echo "=========================================="
echo "外网 IP: ${EXTERNAL_IP}"
echo "工作目录: ${WORK_DIR}"
echo "K8s 配置目录: ${K8S_DIR}"
echo "构建模式: $([ "$USE_CACHE" == "true" ] && echo "使用缓存" || echo "强制重新构建")"
echo "=========================================="

# 1. 构建 Backend 镜像
echo -e "${YELLOW}[步骤 1/5] 构建 Backend Docker 镜像...${NC}"
cd "${WORK_DIR}/backend"
if [ -f "Dockerfile" ]; then
    echo "开始构建 Backend 镜像（可能需要较长时间，首次构建需下载依赖）..."
    if timeout ${BUILD_TIMEOUT} docker build ${BUILD_FLAG} -t burndown-backend:latest .; then
        echo -e "${GREEN}✓ Backend 镜像构建成功${NC}"
    else
        echo -e "${RED}✗ Backend 镜像构建失败或超时${NC}"
        exit 1
    fi
else
    echo -e "${RED}✗ 未找到 Backend Dockerfile${NC}"
    exit 1
fi

# 2. 清理旧的部署
echo -e "${YELLOW}[步骤 2/5] 清理旧的部署...${NC}"
kubectl delete -f "${K8S_DIR}/backend.yaml" --ignore-not-found=true
kubectl delete -f "${K8S_DIR}/postgres.yaml" --ignore-not-found=true
kubectl delete -f "${K8S_DIR}/redis.yaml" --ignore-not-found=true

# 停止并删除本地 Docker 容器（如果存在）
if docker ps -q -f name=postgres > /dev/null 2>&1; then
    echo "停止本地 PostgreSQL 容器..."
    docker stop postgres > /dev/null 2>&1 || true
    docker rm postgres > /dev/null 2>&1 || true
fi

if docker ps -q -f name=redis > /dev/null 2>&1; then
    echo "停止本地 Redis 容器..."
    docker stop redis > /dev/null 2>&1 || true
    docker rm redis > /dev/null 2>&1 || true
fi

echo -e "${GREEN}✓ 旧部署清理完成${NC}"

# 3. 部署 Redis
echo -e "${YELLOW}[步骤 3/5] 部署 Redis...${NC}"
kubectl apply -f "${K8S_DIR}/redis.yaml"
echo -e "${GREEN}✓ Redis 部署完成${NC}"

# 4. 部署 PostgreSQL
echo -e "${YELLOW}[步骤 4/5] 部署 PostgreSQL...${NC}"
kubectl apply -f "${K8S_DIR}/postgres.yaml"
echo "等待 PostgreSQL 就绪..."
kubectl wait --for=condition=ready pod -l app=postgres --timeout=120s || true

# 初始化数据库
echo "初始化数据库..."
sleep 5  # 等待 PostgreSQL 完全启动
POSTGRES_POD=$(kubectl get pod -l app=postgres -o jsonpath='{.items[0].metadata.name}')

# 复制 init.sql 到 PostgreSQL 容器
kubectl cp "${WORK_DIR}/backend/init.sql" ${POSTGRES_POD}:/tmp/init.sql

# 执行初始化脚本
kubectl exec ${POSTGRES_POD} -- psql -U postgres -d burndown_db -f /tmp/init.sql > /dev/null 2>&1 || {
    echo "数据库初始化失败，尝试创建数据库后再初始化..."
    kubectl exec ${POSTGRES_POD} -- psql -U postgres -c "DROP DATABASE IF EXISTS burndown_db;" > /dev/null 2>&1 || true
    kubectl exec ${POSTGRES_POD} -- psql -U postgres -c "CREATE DATABASE burndown_db;" > /dev/null 2>&1 || true
    kubectl exec ${POSTGRES_POD} -- psql -U postgres -d burndown_db -f /tmp/init.sql > /dev/null 2>&1
}

echo -e "${GREEN}✓ 数据库初始化完成${NC}"

# 5. 部署 Backend
echo -e "${YELLOW}[步骤 5/5] 部署 Backend...${NC}"
kubectl apply -f "${K8S_DIR}/backend.yaml"
echo -e "${GREEN}✓ Backend 部署完成${NC}"

echo "等待 Backend 就绪..."
kubectl wait --for=condition=ready pod -l app=burndown-backend --timeout=120s || true

echo ""
echo "=========================================="
echo -e "${GREEN}✓ 部署完成！${NC}"
echo "=========================================="
echo ""
echo "使用说明:"
echo "  ./deploy-backend-only.sh                    # 强制重新构建（确保使用最新代码）"
echo "  ./deploy-backend-only.sh --cache            # 使用缓存构建（更快，适合代码未变化时）"
echo ""
echo "查看部署状态:"
echo "  kubectl get pods"
echo "  kubectl get services"
echo ""
echo "查看 Pod 日志:"
echo "  kubectl logs -l app=burndown-backend"
echo "  kubectl logs -l app=postgres"
echo "  kubectl logs -l app=redis"
echo ""
echo "访问地址:"
echo "  Backend API: http://${EXTERNAL_IP}:${BACKEND_PORT}"
echo "  PostgreSQL: ${EXTERNAL_IP}:${POSTGRES_PORT}"
echo "  Redis: ${EXTERNAL_IP}:${REDIS_PORT}"
echo ""
echo "=========================================="
echo ""
echo "当前 Pod 状态:"
kubectl get pods
echo ""
echo "当前 Service 状态:"
kubectl get services
