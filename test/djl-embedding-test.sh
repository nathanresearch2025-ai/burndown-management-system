#!/bin/bash

# DJL 本地向量生成测试脚本

BASE_URL="http://localhost:8080/api/v1"

echo "=========================================="
echo "DJL 本地向量生成功能测试"
echo "=========================================="
echo ""

# 1. 检查向量服务信息
echo "1. 检查向量服务配置..."
curl -s -X GET "${BASE_URL}/embeddings/info" | jq '.'
echo ""
echo ""

# 2. 测试英文文本向量生成
echo "2. 测试英文文本向量生成..."
curl -s -X POST "${BASE_URL}/embeddings/test" \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Implement user authentication feature"
  }' | jq '.'
echo ""
echo ""

# 3. 测试中文文本向量生成
echo "3. 测试中文文本向量生成..."
curl -s -X POST "${BASE_URL}/embeddings/test" \
  -H "Content-Type: application/json" \
  -d '{
    "text": "实现用户登录功能，包括密码加密和JWT令牌生成"
  }' | jq '.'
echo ""
echo ""

# 4. 测试长文本向量生成
echo "4. 测试长文本向量生成..."
curl -s -X POST "${BASE_URL}/embeddings/test" \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Title: User Authentication\nDescription: Implement a secure user authentication system with password hashing using BCrypt and JWT token generation. The system should support login, logout, and token refresh functionality.\nType: FEATURE\nPriority: HIGH"
  }' | jq '.'
echo ""
echo ""

# 5. 性能测试 - 连续生成10次
echo "5. 性能测试 - 连续生成10次向量..."
total_time=0
for i in {1..10}; do
  response=$(curl -s -X POST "${BASE_URL}/embeddings/test" \
    -H "Content-Type: application/json" \
    -d "{\"text\": \"Test embedding generation performance - iteration $i\"}")

  duration=$(echo $response | jq -r '.durationMs')
  echo "  第 $i 次: ${duration}ms"
  total_time=$((total_time + duration))
done

avg_time=$((total_time / 10))
echo ""
echo "  平均耗时: ${avg_time}ms"
echo ""
echo ""

# 6. 测试任务描述生成（使用向量搜索）
echo "6. 测试 AI 任务描述生成（使用向量相似度搜索）..."
echo "注意：需要先登录获取 JWT token"
echo ""

# 登录获取 token
echo "  6.1 登录获取 token..."
LOGIN_RESPONSE=$(curl -s -X POST "${BASE_URL}/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }')

TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.token')

if [ "$TOKEN" != "null" ] && [ -n "$TOKEN" ]; then
  echo "  登录成功，Token: ${TOKEN:0:20}..."
  echo ""

  # 生成任务描述
  echo "  6.2 生成任务描述..."
  curl -s -X POST "${BASE_URL}/tasks/ai/generate-description" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d '{
      "projectId": 1,
      "title": "优化数据库查询性能",
      "type": "TASK",
      "priority": "HIGH",
      "storyPoints": 5
    }' | jq '.'
  echo ""
else
  echo "  登录失败，跳过任务描述生成测试"
  echo ""
fi

echo ""
echo "=========================================="
echo "测试完成"
echo "=========================================="
