#!/bin/bash

# Kubernetes 部署脚本
# 功能：构建 Docker 镜像并部署到 K8s 集群
# 使用方法：
#   ./deploy.sh                    # 强制重新构建（不使用缓存）
#   ./deploy.sh --cache            # 使用缓存构建（更快）
#   ./deploy.sh --monitoring-only  # 仅部署监控组件

set -e  # 遇到错误立即退出

# 配置变量
EXTERNAL_IP="159.75.202.106"  # 外网 IP 地址
FRONTEND_PORT="30173"          # Frontend NodePort
BACKEND_PORT="30080"           # Backend NodePort
POSTGRES_PORT="30432"          # PostgreSQL NodePort
REDIS_PORT="30379"             # Redis NodePort
RABBITMQ_PORT="30672"          # RabbitMQ AMQP NodePort
RABBITMQ_MGMT_PORT="31672"     # RabbitMQ Management NodePort
PROMETHEUS_PORT="30090"        # Prometheus NodePort
GRAFANA_PORT="30300"           # Grafana NodePort
ALERTMANAGER_PORT="30093"      # Alertmanager NodePort
BUILD_TIMEOUT=1800             # 构建超时时间（秒），默认30分钟

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 工作目录
WORK_DIR="/myapp"
K8S_DIR="${WORK_DIR}/k8s"
MONITORING_DIR="${WORK_DIR}/monitoring"

# 检查部署模式
MONITORING_ONLY=false
USE_CACHE=false
BUILD_FLAG="--no-cache"

if [ "$1" == "--monitoring-only" ]; then
    MONITORING_ONLY=true
    echo "仅部署监控组件模式"
elif [ "$1" == "--cache" ]; then
    USE_CACHE=true
    BUILD_FLAG=""
    echo "使用缓存模式构建"
else
    echo "强制重新构建模式（不使用缓存）"
fi

echo "=========================================="
echo "开始部署到 Kubernetes 集群"
echo "=========================================="
echo "外网 IP: ${EXTERNAL_IP}"
echo "工作目录: ${WORK_DIR}"
echo "K8s 配置目录: ${K8S_DIR}"
echo "监控配置目录: ${MONITORING_DIR}"
echo "部署模式: $([ "$MONITORING_ONLY" == "true" ] && echo "仅部署监控组件" || echo "完整部署")"
echo "构建模式: $([ "$USE_CACHE" == "true" ] && echo "使用缓存" || echo "强制重新构建")"
echo "=========================================="

# 如果是仅部署监控模式，跳过应用部署
if [ "$MONITORING_ONLY" == "true" ]; then
    echo -e "${YELLOW}[监控部署] 部署监控组件...${NC}"

    # 部署 Prometheus、Grafana、Alertmanager
    echo "部署 Prometheus..."
    kubectl apply -f "${MONITORING_DIR}/k8s-monitoring-all.yaml"

    echo "等待监控组件就绪..."
    kubectl wait --for=condition=ready pod -l app=prometheus --timeout=120s || true
    kubectl wait --for=condition=ready pod -l app=grafana --timeout=120s || true
    kubectl wait --for=condition=ready pod -l app=alertmanager --timeout=120s || true

    echo ""
    echo "=========================================="
    echo -e "${GREEN}✓ 监控组件部署完成！${NC}"
    echo "=========================================="
    echo ""
    echo "访问地址:"
    echo "  Prometheus: http://${EXTERNAL_IP}:${PROMETHEUS_PORT}"
    echo "  Grafana: http://${EXTERNAL_IP}:${GRAFANA_PORT} (用户名: admin, 密码: admin123)"
    echo "  Alertmanager: http://${EXTERNAL_IP}:${ALERTMANAGER_PORT}"
    echo ""
    echo "查看监控组件状态:"
    echo "  kubectl get pods -l app=prometheus"
    echo "  kubectl get pods -l app=grafana"
    echo "  kubectl get pods -l app=alertmanager"
    echo ""
    echo "=========================================="
    exit 0
fi

# 1. 构建 Backend 镜像
echo -e "${YELLOW}[步骤 1/7] 构建 Backend Docker 镜像...${NC}"
cd "${WORK_DIR}/backend"
if [ -f "Dockerfile" ]; then
    # 根据参数决定是否使用缓存
    echo "开始构建 Backend 镜像（可能需要较长时间，首次构建需下载依赖）..."
    if timeout ${BUILD_TIMEOUT} docker build ${BUILD_FLAG} -t burndown-backend:latest .; then
        echo -e "${GREEN}✓ Backend 镜像构建成功${NC}"
    else
        echo -e "${RED}✗ Backend 镜像构建失败或超时${NC}"
        exit 1
    fi

    # 导入镜像到 K3s containerd
    echo "导入 Backend 镜像到 K3s..."
    if docker save burndown-backend:latest | k3s ctr images import /dev/stdin > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Backend 镜像已导入 K3s${NC}"
    else
        echo -e "${RED}✗ Backend 镜像导入失败${NC}"
        exit 1
    fi
else
    echo -e "${RED}✗ 错误: backend/Dockerfile 不存在${NC}"
    exit 1
fi

# 2. 构建 Frontend 镜像
echo -e "${YELLOW}[步骤 2/7] 构建 Frontend Docker 镜像...${NC}"
cd "${WORK_DIR}/frontend"
if [ -f "Dockerfile" ]; then
    # 根据参数决定是否使用缓存
    echo "开始构建 Frontend 镜像..."
    if timeout ${BUILD_TIMEOUT} docker build ${BUILD_FLAG} -t burndown-frontend:latest .; then
        echo -e "${GREEN}✓ Frontend 镜像构建成功${NC}"
    else
        echo -e "${RED}✗ Frontend 镜像构建失败或超时${NC}"
        exit 1
    fi

    # 导入镜像到 K3s containerd
    echo "导入 Frontend 镜像到 K3s..."
    if docker save burndown-frontend:latest | k3s ctr images import /dev/stdin > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Frontend 镜像已导入 K3s${NC}"
    else
        echo -e "${RED}✗ Frontend 镜像导入失败${NC}"
        exit 1
    fi
else
    echo -e "${RED}✗ 错误: frontend/Dockerfile 不存在${NC}"
    exit 1
fi

# 清理悬空镜像（dangling images）
echo "清理悬空镜像..."
docker image prune -f > /dev/null 2>&1 || true

# 3. 停止 Docker 中的 Redis（如果存在）
echo -e "${YELLOW}[步骤 3/8] 停止 Docker 中的 Redis...${NC}"
if docker ps -q -f name=redis > /dev/null 2>&1; then
    echo "停止并删除 Docker Redis 容器..."
    docker stop redis > /dev/null 2>&1 || true
    docker rm redis > /dev/null 2>&1 || true
    echo -e "${GREEN}✓ Docker Redis 已停止${NC}"
else
    echo "Docker Redis 未运行，跳过"
fi

# 4. 删除旧的部署（如果存在）
echo -e "${YELLOW}[步骤 4/8] 删除旧的 K8s 部署...${NC}"
kubectl delete -f "${K8S_DIR}/backend.yaml" --ignore-not-found=true
kubectl delete -f "${K8S_DIR}/frontend.yaml" --ignore-not-found=true
kubectl delete -f "${K8S_DIR}/postgres.yaml" --ignore-not-found=true
kubectl delete -f "${K8S_DIR}/redis.yaml" --ignore-not-found=true
kubectl delete -f "${K8S_DIR}/rabbitmq.yaml" --ignore-not-found=true
echo -e "${GREEN}✓ 旧部署已删除${NC}"

# 等待 Pod 完全终止
echo "等待 Pod 完全终止..."
sleep 5

# 5. 部署 Redis
echo -e "${YELLOW}[步骤 5/10] 部署 Redis...${NC}"
kubectl apply -f "${K8S_DIR}/redis.yaml"
echo -e "${GREEN}✓ Redis 部署完成${NC}"

# 等待 Redis 就绪
echo "等待 Redis 就绪..."
kubectl wait --for=condition=ready pod -l app=redis --timeout=120s || true
sleep 3

# 5.5 部署 RabbitMQ
echo -e "${YELLOW}[步骤 6/10] 部署 RabbitMQ...${NC}"
kubectl apply -f "${K8S_DIR}/rabbitmq.yaml"
echo -e "${GREEN}✓ RabbitMQ 部署完成${NC}"

# 等待 RabbitMQ 就绪
echo "等待 RabbitMQ 就绪..."
kubectl wait --for=condition=ready pod -l app=rabbitmq --timeout=180s || true
sleep 3

# 6. 部署 PostgreSQL
echo -e "${YELLOW}[步骤 7/10] 部署 PostgreSQL...${NC}"
kubectl apply -f "${K8S_DIR}/postgres.yaml"
echo -e "${GREEN}✓ PostgreSQL 部署完成${NC}"

# 等待 PostgreSQL 就绪
echo "等待 PostgreSQL 就绪..."
kubectl wait --for=condition=ready pod -l app=postgres --timeout=120s || true
sleep 10

# 7. 导入数据库初始化脚本
echo -e "${YELLOW}[步骤 8/10] 导入数据库初始化脚本...${NC}"
POSTGRES_POD=$(kubectl get pod -l app=postgres -o jsonpath='{.items[0].metadata.name}')
if [ -z "$POSTGRES_POD" ]; then
    echo -e "${RED}✗ 错误: 找不到 PostgreSQL Pod${NC}"
    exit 1
fi

echo "PostgreSQL Pod: $POSTGRES_POD"
echo "复制 init.sql 到 Pod..."
kubectl cp "${WORK_DIR}/backend/init.sql" "${POSTGRES_POD}:/tmp/init.sql"

echo "执行数据库初始化..."
kubectl exec -i "${POSTGRES_POD}" -- psql -U postgres -d burndown_db -f /tmp/init.sql

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 数据库初始化完成${NC}"
else
    echo -e "${YELLOW}⚠ 数据库初始化可能失败（如果表已存在则忽略）${NC}"
fi

# 8. 部署 Backend
echo -e "${YELLOW}[步骤 9/10] 部署 Backend...${NC}"
kubectl apply -f "${K8S_DIR}/backend.yaml"
echo -e "${GREEN}✓ Backend 部署完成${NC}"

# 等待 Backend 就绪
echo "等待 Backend 就绪..."
kubectl wait --for=condition=ready pod -l app=burndown-backend --timeout=120s || true
sleep 3

# 9. 部署 Frontend
echo -e "${YELLOW}[步骤 10/10] 部署 Frontend...${NC}"
kubectl apply -f "${K8S_DIR}/frontend.yaml"
echo -e "${GREEN}✓ Frontend 部署完成${NC}"

# 等待 Frontend 就绪
echo "等待 Frontend 就绪..."
kubectl wait --for=condition=ready pod -l app=burndown-frontend --timeout=120s || true

echo ""
echo "=========================================="
echo -e "${GREEN}✓ 部署完成！${NC}"
echo "=========================================="
echo ""
echo "使用说明:"
echo "  ./deploy.sh                    # 强制重新构建（确保使用最新代码）"
echo "  ./deploy.sh --cache            # 使用缓存构建（更快，适合代码未变化时）"
echo "  ./deploy.sh --monitoring-only  # 仅部署监控组件"
echo ""
echo "查看部署状态:"
echo "  kubectl get pods"
echo "  kubectl get services"
echo ""
echo "查看 Pod 日志:"
echo "  kubectl logs -l app=burndown-backend"
echo "  kubectl logs -l app=burndown-frontend"
echo "  kubectl logs -l app=postgres"
echo "  kubectl logs -l app=redis"
echo "  kubectl logs -l app=rabbitmq"
echo ""
echo "访问地址:"
echo "  Frontend: http://${EXTERNAL_IP}:${FRONTEND_PORT}"
echo "  Backend API: http://${EXTERNAL_IP}:${BACKEND_PORT}"
echo "  PostgreSQL: ${EXTERNAL_IP}:${POSTGRES_PORT}"
echo "  Redis: ${EXTERNAL_IP}:${REDIS_PORT}"
echo "  RabbitMQ AMQP: ${EXTERNAL_IP}:${RABBITMQ_PORT}"
echo "  RabbitMQ 管理界面: http://${EXTERNAL_IP}:${RABBITMQ_MGMT_PORT} (用户名: admin, 密码: admin123)"
echo ""
echo "监控访问地址:"
echo "  Prometheus: http://${EXTERNAL_IP}:${PROMETHEUS_PORT}"
echo "  Grafana: http://${EXTERNAL_IP}:${GRAFANA_PORT} (用户名: admin, 密码: admin123)"
echo "  Alertmanager: http://${EXTERNAL_IP}:${ALERTMANAGER_PORT}"
echo ""
echo "部署监控组件:"
echo "  ./deploy.sh --monitoring-only"
echo ""
echo "=========================================="

# 显示当前状态
echo ""
echo "当前 Pod 状态:"
kubectl get pods
echo ""
echo "当前 Service 状态:"
kubectl get services
echo ""
