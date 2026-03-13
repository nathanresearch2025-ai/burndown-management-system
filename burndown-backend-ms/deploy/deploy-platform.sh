#!/bin/bash
set -e

WORK_DIR="/myapp/burndown-backend-ms"
BUILD_TIMEOUT=1800
EXTERNAL_IP="159.75.202.106"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo "=========================================="
echo "Deploying Platform Infrastructure"
echo "=========================================="

cd "${WORK_DIR}"

# Deploy Redis
echo -e "${YELLOW}[1/2] Deploying Redis...${NC}"
kubectl apply -f k8s/platform/redis.yaml
kubectl wait --for=condition=ready pod -l app=redis --timeout=120s || true
sleep 3
echo -e "${GREEN}✓ Redis deployed${NC}"

# Deploy Nacos
echo -e "${YELLOW}[2/2] Deploying Nacos...${NC}"
kubectl apply -f k8s/platform/nacos.yaml
echo "Waiting for Nacos to be ready (this may take 2-3 minutes)..."
kubectl wait --for=condition=ready pod -l app=nacos --timeout=300s || true
sleep 30
echo -e "${GREEN}✓ Nacos deployed${NC}"

echo ""
echo "=========================================="
echo -e "${GREEN}✓ Platform deployment completed!${NC}"
echo "=========================================="
echo "Redis: http://${EXTERNAL_IP}:30379"
echo "Nacos: http://${EXTERNAL_IP}:30848/nacos (username: nacos, password: nacos)"
