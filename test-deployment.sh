#!/bin/bash

echo "等待部署完成..."
sleep 120

echo "检查 Pod 状态..."
kubectl get pods

echo ""
echo "检查服务状态..."
kubectl get svc

echo ""
echo "检查后端日志（最后20行）..."
kubectl logs -l app=burndown-backend --tail=20

echo ""
echo "测试 Agent API..."
curl -X POST http://localhost:30080/api/v1/agent/standup/query \
  -H "Content-Type: application/json" \
  -d '{
    "question": "测试问题",
    "projectId": 1,
    "sprintId": 1
  }' | jq .
