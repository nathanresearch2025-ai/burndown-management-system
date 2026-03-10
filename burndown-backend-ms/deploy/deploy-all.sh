#!/bin/bash
set -e

WORK_DIR="/myapp/burndown-backend-ms"
EXTERNAL_IP="159.75.202.106"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo "=========================================="
echo "Full Microservices Deployment"
echo "=========================================="
echo "This will deploy:"
echo "  1. Platform infrastructure (Redis, Nacos)"
echo "  2. Databases (auth_db, core_db)"
echo "  3. Microservices (auth-service, core-service, api-gateway)"
echo "=========================================="

cd "${WORK_DIR}"

# Step 1: Deploy platform
echo -e "${YELLOW}Step 1: Deploying platform infrastructure...${NC}"
bash deploy/deploy-platform.sh
echo ""

# Step 2: Deploy databases
echo -e "${YELLOW}Step 2: Deploying and initializing databases...${NC}"
bash database/deploy_databases.sh
echo ""

# Step 3: Build and deploy services
echo -e "${YELLOW}Step 3: Building and deploying microservices...${NC}"
bash deploy/deploy-services.sh "$@"
echo ""

echo "=========================================="
echo -e "${GREEN}✓ Full deployment completed!${NC}"
echo "=========================================="
echo ""
echo "Access URLs:"
echo "  API Gateway:  http://${EXTERNAL_IP}:30080"
echo "  Auth Service: http://${EXTERNAL_IP}:30081"
echo "  Core Service: http://${EXTERNAL_IP}:30082"
echo "  Nacos:        http://${EXTERNAL_IP}:30848/nacos"
echo "  Redis:        ${EXTERNAL_IP}:30379"
echo "  Auth DB:      ${EXTERNAL_IP}:30433"
echo "  Core DB:      ${EXTERNAL_IP}:30432"
echo ""
echo "Test credentials:"
echo "  Username: admin"
echo "  Password: password123"
echo ""
echo "Check deployment status:"
echo "  kubectl get pods"
echo "  kubectl get services"
echo ""
echo "View logs:"
echo "  kubectl logs -l app=api-gateway"
echo "  kubectl logs -l app=auth-service"
echo "  kubectl logs -l app=core-service"
