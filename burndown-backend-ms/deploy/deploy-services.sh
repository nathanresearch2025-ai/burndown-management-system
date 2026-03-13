#!/bin/bash
set -e

WORK_DIR="/myapp/burndown-backend-ms"
BUILD_TIMEOUT=1800
EXTERNAL_IP="159.75.202.106"
USE_CACHE=false
BUILD_FLAG="--no-cache"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

if [ "$1" == "--cache" ]; then
    USE_CACHE=true
    BUILD_FLAG=""
    echo "Using Docker cache for builds"
else
    echo "Building without cache (clean build)"
fi

echo "=========================================="
echo "Building and Deploying Microservices"
echo "=========================================="

cd "${WORK_DIR}"

# Build auth-service
echo -e "${YELLOW}[1/6] Building auth-service Docker image...${NC}"
cd services/auth-service
if timeout ${BUILD_TIMEOUT} docker build ${BUILD_FLAG} -t auth-service:latest .; then
    echo -e "${GREEN}✓ auth-service image built${NC}"
else
    echo -e "${RED}✗ auth-service build failed${NC}"
    exit 1
fi
docker save auth-service:latest | k3s ctr images import /dev/stdin > /dev/null 2>&1
echo -e "${GREEN}✓ auth-service image imported to K3s${NC}"

# Build core-service
echo -e "${YELLOW}[2/6] Building core-service Docker image...${NC}"
cd ../core-service
if timeout ${BUILD_TIMEOUT} docker build ${BUILD_FLAG} -t core-service:latest .; then
    echo -e "${GREEN}✓ core-service image built${NC}"
else
    echo -e "${RED}✗ core-service build failed${NC}"
    exit 1
fi
docker save core-service:latest | k3s ctr images import /dev/stdin > /dev/null 2>&1
echo -e "${GREEN}✓ core-service image imported to K3s${NC}"

# Build api-gateway
echo -e "${YELLOW}[3/6] Building api-gateway Docker image...${NC}"
cd ../../platform/api-gateway
if timeout ${BUILD_TIMEOUT} docker build ${BUILD_FLAG} -t api-gateway:latest .; then
    echo -e "${GREEN}✓ api-gateway image built${NC}"
else
    echo -e "${RED}✗ api-gateway build failed${NC}"
    exit 1
fi
docker save api-gateway:latest | k3s ctr images import /dev/stdin > /dev/null 2>&1
echo -e "${GREEN}✓ api-gateway image imported to K3s${NC}"

cd "${WORK_DIR}"

# Clean up old deployments
echo -e "${YELLOW}[4/6] Cleaning up old deployments...${NC}"
kubectl delete -f k8s/services/auth-service.yaml --ignore-not-found=true
kubectl delete -f k8s/services/core-service.yaml --ignore-not-found=true
kubectl delete -f k8s/services/api-gateway.yaml --ignore-not-found=true
sleep 5
echo -e "${GREEN}✓ Old deployments cleaned${NC}"

# Deploy auth-service
echo -e "${YELLOW}[5/6] Deploying auth-service...${NC}"
kubectl apply -f k8s/services/auth-service.yaml
kubectl wait --for=condition=ready pod -l app=auth-service --timeout=120s || true
sleep 3
echo -e "${GREEN}✓ auth-service deployed${NC}"

# Deploy core-service
echo -e "${YELLOW}[6/6] Deploying core-service...${NC}"
kubectl apply -f k8s/services/core-service.yaml
kubectl wait --for=condition=ready pod -l app=core-service --timeout=120s || true
sleep 3
echo -e "${GREEN}✓ core-service deployed${NC}"

# Deploy api-gateway
echo -e "${YELLOW}[7/7] Deploying api-gateway...${NC}"
kubectl apply -f k8s/services/api-gateway.yaml
kubectl wait --for=condition=ready pod -l app=api-gateway --timeout=120s || true
sleep 3
echo -e "${GREEN}✓ api-gateway deployed${NC}"

# Clean up dangling images
docker image prune -f > /dev/null 2>&1 || true

echo ""
echo "=========================================="
echo -e "${GREEN}✓ Microservices deployment completed!${NC}"
echo "=========================================="
echo "API Gateway: http://${EXTERNAL_IP}:30080"
echo "Auth Service: http://${EXTERNAL_IP}:30081"
echo "Core Service: http://${EXTERNAL_IP}:30082"
echo ""
echo "Check service status:"
echo "  kubectl get pods"
echo "  kubectl get services"
