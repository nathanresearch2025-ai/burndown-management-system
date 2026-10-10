# Sprint Health Assessment - 使用示例

## 场景1：健康的Sprint

### 背景
- Sprint进展顺利
- 所有指标都在良好范围内
- 团队表现稳定

### API调用

```bash
curl -X GET "http://localhost:8080/api/v1/sprints/1/health?lang=zh-CN" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "Content-Type: application/json"
```

### 响应示例

```json
{
  "sprintId": 1,
  "sprintName": "Sprint 2024-W03",
  "sprintStatus": "ACTIVE",
  "assessmentTime": "2024-01-15T14:30:00",
  "overallHealth": {
    "score": 85.5,
    "level": "GOOD",
    "trend": "STABLE"
  },
  "dimensions": [
    {
      "name": "burndownTrend",
      "displayName": "燃尽趋势",
      "score": 88.0,
      "status": "GOOD",
      "weight": 0.25,
      "details": {
        "idealRemaining": 50.0,
        "actualRemaining": 48.0,
        "deviation": -4.0,
        "trendSlope": -3.2
      },
      "description": "燃尽趋势良好"
    },
    {
      "name": "taskCompletion",
      "displayName": "任务完成率",
      "score": 85.0,
      "status": "GOOD",
      "weight": 0.20,
      "details": {
        "totalTasks": 25,
        "completedTasks": 18,
        "completionRate": 72.0,
        "expectedCompletionRate": 70.0
      },
      "description": "任务完成率符合预期"
    },
    {
      "name": "blockedTasks",
      "displayName": "阻塞任务",
      "score": 92.0,
      "status": "EXCELLENT",
      "weight": 0.15,
      "details": {
        "totalTasks": 25,
        "blockedTasks": 1,
        "blockedRate": 4.0,
        "avgBlockedDays": 0.5
      },
      "description": "阻塞任务极少，进展顺利"
    },
    {
      "name": "worklogCompleteness",
      "displayName": "工作日志完整性",
      "score": 82.0,
      "status": "GOOD",
      "weight": 0.15,
      "details": {
        "expectedWorkdays": 50,
        "actualWorklogDays": 41,
        "completenessRate": 82.0,
        "missingDays": 9
      },
      "description": "工作日志记录良好"
    },
    {
      "name": "storyPointAccuracy",
      "displayName": "故事点估算准确度",
      "score": 80.0,
      "status": "GOOD",
      "weight": 0.15,
      "details": {
        "plannedPoints": 100,
        "completedPoints": 72,
        "estimatedCompletePoints": 95,
        "accuracyRate": 95.0,
        "avgPointDeviation": 5.0
      },
      "description": "故事点估算较为准确"
    },
    {
      "name": "velocityStability",
      "displayName": "团队速度稳定性",
      "score": 85.0,
      "status": "GOOD",
      "weight": 0.10,
      "details": {
        "currentVelocity": 72,
        "averageVelocity": 70,
        "velocityVariance": 15.2,
        "stabilityScore": 85.0
      },
      "description": "团队速度保持稳定"
    }
  ],
  "recommendations": [],
  "historicalComparison": {
    "previousSprintScore": 82.0,
    "scoreChange": 3.5,
    "percentageChange": 4.27,
    "trend": "IMPROVING"
  },
  "riskAlerts": []
}
```

### 解读

✅ **总体评分**：85.5分，处于GOOD等级
✅ **趋势**：STABLE，团队表现稳定
✅ **无风险警报**：所有维度都在健康范围内
✅ **无改进建议**：当前状态良好，继续保持

---

## 场景2：存在风险的Sprint

### 背景
- Sprint进度落后
- 工作日志记录不完整
- 存在较多阻塞任务

### API调用

```bash
curl -X GET "http://localhost:8080/api/v1/sprints/2/health?includeRecommendations=true&lang=zh-CN" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "Content-Type: application/json"
```

### 响应示例

```json
{
  "sprintId": 2,
  "sprintName": "Sprint 2024-W04",
  "sprintStatus": "ACTIVE",
  "assessmentTime": "2024-01-22T10:15:00",
  "overallHealth": {
    "score": 62.3,
    "level": "MODERATE",
    "trend": "DECLINING"
  },
  "dimensions": [
    {
      "name": "burndownTrend",
      "displayName": "燃尽趋势",
      "score": 65.0,
      "status": "MODERATE",
      "weight": 0.25,
      "details": {
        "idealRemaining": 40.0,
        "actualRemaining": 52.0,
        "deviation": 30.0,
        "trendSlope": -1.5
      },
      "description": "实际进度与理想线存在偏差"
    },
    {
      "name": "taskCompletion",
      "displayName": "任务完成率",
      "score": 58.0,
      "status": "WARNING",
      "weight": 0.20,
      "details": {
        "totalTasks": 30,
        "completedTasks": 12,
        "completionRate": 40.0,
        "expectedCompletionRate": 60.0
      },
      "description": "完成率明显低于预期，需要加速"
    },
    {
      "name": "blockedTasks",
      "displayName": "阻塞任务",
      "score": 68.0,
      "status": "MODERATE",
      "weight": 0.15,
      "details": {
        "totalTasks": 30,
        "blockedTasks": 5,
        "blockedRate": 16.7,
        "avgBlockedDays": 2.5
      },
      "description": "存在一定数量的阻塞任务"
    },
    {
      "name": "worklogCompleteness",
      "displayName": "工作日志完整性",
      "score": 55.0,
      "status": "WARNING",
      "weight": 0.15,
      "details": {
        "expectedWorkdays": 60,
        "actualWorklogDays": 33,
        "completenessRate": 55.0,
        "missingDays": 27
      },
      "description": "工作日志记录不够完整，影响数据准确性"
    },
    {
      "name": "storyPointAccuracy",
      "displayName": "故事点估算准确度",
      "score": 65.0,
      "status": "MODERATE",
      "weight": 0.15,
      "details": {
        "plannedPoints": 120,
        "completedPoints": 48,
        "estimatedCompletePoints": 90,
        "accuracyRate": 75.0,
        "avgPointDeviation": 25.0
      },
      "description": "故事点估算准确度一般"
    },
    {
      "name": "velocityStability",
      "displayName": "团队速度稳定性",
      "score": 72.0,
      "status": "MODERATE",
      "weight": 0.10,
      "details": {
        "currentVelocity": 48,
        "averageVelocity": 65,
        "velocityVariance": 180.5,
        "stabilityScore": 72.0
      },
      "description": "团队速度波动适中"
    }
  ],
  "recommendations": [
    {
      "priority": "HIGH",
      "category": "TASK_COMPLETION",
      "title": "加速任务完成进度",
      "description": "当前完成率40%低于预期60%，建议重新评估任务优先级",
      "actionItems": [
        "识别并移除低优先级任务",
        "为阻塞任务寻求帮助或升级",
        "考虑增加团队资源或延长Sprint"
      ],
      "expectedImpact": "提升Sprint目标达成率"
    },
    {
      "priority": "HIGH",
      "category": "WORKLOG",
      "title": "改善工作日志记录习惯",
      "description": "当前工作日志完整性仅55%，建议团队每日更新工作日志",
      "actionItems": [
        "在每日站会中提醒团队成员更新工作日志",
        "设置工作日志提醒通知",
        "将工作日志完整性纳入团队绩效指标"
      ],
      "expectedImpact": "提高数据准确性，更好地追踪项目进展"
    },
    {
      "priority": "MEDIUM",
      "category": "BLOCKED_TASKS",
      "title": "解决阻塞任务",
      "description": "当前存在5个阻塞任务，影响整体进度",
      "actionItems": [
        "在每日站会中重点讨论阻塞任务",
        "为阻塞任务分配专人跟进",
        "必要时升级问题到管理层"
      ],
      "expectedImpact": "加快任务流转，提高团队效率"
    }
  ],
  "historicalComparison": {
    "previousSprintScore": 72.0,
    "scoreChange": -9.7,
    "percentageChange": -13.47,
    "trend": "DECLINING"
  },
  "riskAlerts": [
    {
      "severity": "HIGH",
      "category": "TASK_COMPLETION",
      "message": "任务完成率低于70%阈值",
      "detectedAt": "2024-01-22T10:15:00"
    },
    {
      "severity": "HIGH",
      "category": "WORKLOG",
      "message": "工作日志完整性低于70%阈值",
      "detectedAt": "2024-01-22T10:15:00"
    },
    {
      "severity": "MEDIUM",
      "category": "BURNDOWN_TREND",
      "message": "燃尽趋势低于70%阈值",
      "detectedAt": "2024-01-22T10:15:00"
    }
  ]
}
```

### 解读

⚠️ **总体评分**：62.3分，处于MODERATE等级
⚠️ **趋势**：DECLINING，健康度下降13.47%
⚠️ **3个风险警报**：任务完成率、工作日志、燃尽趋势
⚠️ **3条改进建议**：2个HIGH优先级，1个MEDIUM优先级

### 行动建议

1. **立即行动**（HIGH优先级）
   - 召开紧急会议，评估Sprint范围
   - 强制要求团队每日更新工作日志
   - 识别并解决阻塞任务

2. **持续监控**
   - 每日检查健康度变化
   - 跟踪改进措施的效果

---

## 场景3：危机Sprint

### 背景
- Sprint严重偏离目标
- 多个维度处于危险状态
- 团队陷入困境

### API调用

```bash
curl -X GET "http://localhost:8080/api/v1/sprints/3/health?lang=en-US" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "Content-Type: application/json"
```

### 响应示例（英文）

```json
{
  "sprintId": 3,
  "sprintName": "Sprint 2024-W05",
  "sprintStatus": "ACTIVE",
  "assessmentTime": "2024-01-29T16:45:00",
  "overallHealth": {
    "score": 38.5,
    "level": "CRITICAL",
    "trend": "DECLINING"
  },
  "dimensions": [
    {
      "name": "burndownTrend",
      "displayName": "Burndown Trend",
      "score": 35.0,
      "status": "CRITICAL",
      "weight": 0.25,
      "details": {
        "idealRemaining": 30.0,
        "actualRemaining": 75.0,
        "deviation": 150.0,
        "trendSlope": -0.5
      },
      "description": "Burndown trend critically off, high risk"
    },
    {
      "name": "taskCompletion",
      "displayName": "Task Completion Rate",
      "score": 35.0,
      "status": "CRITICAL",
      "weight": 0.20,
      "details": {
        "totalTasks": 40,
        "completedTasks": 8,
        "completionRate": 20.0,
        "expectedCompletionRate": 80.0
      },
      "description": "Completion rate significantly below expectations"
    },
    {
      "name": "blockedTasks",
      "displayName": "Blocked Tasks",
      "score": 45.0,
      "status": "WARNING",
      "weight": 0.15,
      "details": {
        "totalTasks": 40,
        "blockedTasks": 12,
        "blockedRate": 30.0,
        "avgBlockedDays": 5.0
      },
      "description": "Many blocked tasks, severely impacting progress"
    },
    {
      "name": "worklogCompleteness",
      "displayName": "Worklog Completeness",
      "score": 42.0,
      "status": "WARNING",
      "weight": 0.15,
      "details": {
        "expectedWorkdays": 80,
        "actualWorklogDays": 34,
        "completenessRate": 42.5,
        "missingDays": 46
      },
      "description": "Worklog incomplete, affecting data accuracy"
    },
    {
      "name": "storyPointAccuracy",
      "displayName": "Story Point Accuracy",
      "score": 38.0,
      "status": "CRITICAL",
      "weight": 0.15,
      "details": {
        "plannedPoints": 150,
        "completedPoints": 30,
        "estimatedCompletePoints": 60,
        "accuracyRate": 40.0,
        "avgPointDeviation": 60.0
      },
      "description": "Story point estimation has significant deviation"
    },
    {
      "name": "velocityStability",
      "displayName": "Velocity Stability",
      "score": 45.0,
      "status": "WARNING",
      "weight": 0.10,
      "details": {
        "currentVelocity": 30,
        "averageVelocity": 65,
        "velocityVariance": 350.0,
        "stabilityScore": 45.0
      },
      "description": "Team velocity fluctuates significantly"
    }
  ],
  "recommendations": [
    {
      "priority": "HIGH",
      "category": "BURNDOWN_TREND",
      "title": "Adjust Sprint Progress",
      "description": "Current burndown trend deviates from ideal by 150%, critical situation",
      "actionItems": [
        "Immediately hold Sprint emergency meeting",
        "Consider Sprint cancellation or major scope reduction",
        "Identify root causes and remove blocking factors"
      ],
      "expectedImpact": "Prevent Sprint failure, reset team direction"
    },
    {
      "priority": "HIGH",
      "category": "TASK_COMPLETION",
      "title": "Accelerate Task Completion",
      "description": "Only 20% completion rate with 80% expected, immediate action required",
      "actionItems": [
        "Remove all non-critical tasks from Sprint",
        "Focus team on top 5 priority items only",
        "Add additional resources or extend timeline significantly"
      ],
      "expectedImpact": "Salvage Sprint goals, deliver minimum viable increment"
    },
    {
      "priority": "HIGH",
      "category": "BLOCKED_TASKS",
      "title": "Resolve Blocked Tasks",
      "description": "30% of tasks are blocked for 5+ days on average",
      "actionItems": [
        "Create war room for blocked task resolution",
        "Escalate all blockers to executive level",
        "Consider external help or vendor support"
      ],
      "expectedImpact": "Unblock critical path, restore team productivity"
    }
  ],
  "historicalComparison": {
    "previousSprintScore": 62.3,
    "scoreChange": -23.8,
    "percentageChange": -38.2,
    "trend": "DECLINING"
  },
  "riskAlerts": [
    {
      "severity": "HIGH",
      "category": "BURNDOWN_TREND",
      "message": "Burndown trend below 70% threshold",
      "detectedAt": "2024-01-29T16:45:00"
    },
    {
      "severity": "HIGH",
      "category": "TASK_COMPLETION",
      "message": "Task completion below 70% threshold",
      "detectedAt": "2024-01-29T16:45:00"
    },
    {
      "severity": "HIGH",
      "category": "STORY_POINT_ACCURACY",
      "message": "Story point accuracy below 70% threshold",
      "detectedAt": "2024-01-29T16:45:00"
    },
    {
      "severity": "MEDIUM",
      "category": "BLOCKED_TASKS",
      "message": "Blocked tasks below 70% threshold",
      "detectedAt": "2024-01-29T16:45:00"
    },
    {
      "severity": "MEDIUM",
      "category": "WORKLOG",
      "message": "Worklog completeness below 70% threshold",
      "detectedAt": "2024-01-29T16:45:00"
    },
    {
      "severity": "MEDIUM",
      "category": "VELOCITY_STABILITY",
      "message": "Velocity stability below 70% threshold",
      "detectedAt": "2024-01-29T16:45:00"
    }
  ]
}
```

### 解读

🚨 **总体评分**：38.5分，处于CRITICAL等级
🚨 **趋势**：DECLINING，健康度暴跌38.2%
🚨 **6个风险警报**：所有维度都存在问题
🚨 **3条HIGH优先级建议**：需要立即干预

### 紧急行动计划

1. **立即停止当前工作**
   - 召开紧急Sprint会议
   - 考虑Sprint取消或重启

2. **执行层升级**
   - 通知项目发起人和高管
   - 申请额外资源支持

3. **根因分析**
   - 30%的任务被阻塞超过5天
   - 团队速度下降54%（30 vs 65）
   - 估算严重失准

4. **恢复计划**
   - 将Sprint范围削减70%
   - 只保留最关键的5-8个任务
   - 每日跟踪健康度变化

---

## 场景4：新Sprint（数据不足）

### API调用

```bash
curl -X GET "http://localhost:8080/api/v1/sprints/4/health" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

### 响应示例

```json
{
  "sprintId": 4,
  "sprintName": "Sprint 2024-W06",
  "sprintStatus": "ACTIVE",
  "assessmentTime": "2024-02-05T09:00:00",
  "overallHealth": {
    "score": 75.0,
    "level": "GOOD",
    "trend": "STABLE"
  },
  "dimensions": [
    {
      "name": "burndownTrend",
      "displayName": "燃尽趋势",
      "score": 100.0,
      "status": "GOOD",
      "weight": 0.25,
      "details": {},
      "description": "暂无燃尽数据"
    },
    {
      "name": "taskCompletion",
      "displayName": "任务完成率",
      "score": 100.0,
      "status": "EXCELLENT",
      "weight": 0.20,
      "details": {
        "totalTasks": 20,
        "completedTasks": 1,
        "completionRate": 5.0,
        "expectedCompletionRate": 5.0
      },
      "description": "任务完成率符合预期"
    }
  ],
  "recommendations": [],
  "historicalComparison": {
    "previousSprintScore": 38.5,
    "scoreChange": 36.5,
    "percentageChange": 94.8,
    "trend": "IMPROVING"
  },
  "riskAlerts": []
}
```

### 解读

ℹ️ **新Sprint**：刚开始1天，数据积累中
ℹ️ **基准分**：75分，给予正常起始评分
ℹ️ **建议**：持续跟踪，3-5天后再次评估

---

## Postman Collection

可以导入以下Postman Collection快速测试：

```json
{
  "info": {
    "name": "Sprint Health API",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "Get Sprint Health (Chinese)",
      "request": {
        "method": "GET",
        "header": [
          {
            "key": "Authorization",
            "value": "Bearer {{jwt_token}}"
          }
        ],
        "url": {
          "raw": "{{base_url}}/sprints/{{sprint_id}}/health?includeRecommendations=true&lang=zh-CN",
          "host": ["{{base_url}}"],
          "path": ["sprints", "{{sprint_id}}", "health"],
          "query": [
            {
              "key": "includeRecommendations",
              "value": "true"
            },
            {
              "key": "lang",
              "value": "zh-CN"
            }
          ]
        }
      }
    },
    {
      "name": "Get Sprint Health (English)",
      "request": {
        "method": "GET",
        "header": [
          {
            "key": "Authorization",
            "value": "Bearer {{jwt_token}}"
          }
        ],
        "url": {
          "raw": "{{base_url}}/sprints/{{sprint_id}}/health?includeRecommendations=true&lang=en-US",
          "host": ["{{base_url}}"],
          "path": ["sprints", "{{sprint_id}}", "health"],
          "query": [
            {
              "key": "includeRecommendations",
              "value": "true"
            },
            {
              "key": "lang",
              "value": "en-US"
            }
          ]
        }
      }
    }
  ],
  "variable": [
    {
      "key": "base_url",
      "value": "http://localhost:8080/api/v1"
    },
    {
      "key": "sprint_id",
      "value": "1"
    },
    {
      "key": "jwt_token",
      "value": ""
    }
  ]
}
```

持续监控，一步步显示。
