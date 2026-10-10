# Sprint Health Assessment API Design

## Feature Overview

Sprint健康度评估功能 - 基于多个维度分析Sprint的健康状况，提供综合评分和改进建议。

## Business Value

- 帮助Scrum Master快速识别Sprint风险
- 提供数据驱动的决策支持
- 及早发现并解决潜在问题
- 提高团队交付质量和可预测性

## API Endpoint

### Get Sprint Health Assessment

**Endpoint:** `GET /api/v1/sprints/{sprintId}/health`

**Description:** 获取指定Sprint的健康度评估报告

**Path Parameters:**
- `sprintId` (Long, required) - Sprint ID

**Query Parameters:**
- `includeRecommendations` (Boolean, optional, default: true) - 是否包含改进建议
- `lang` (String, optional, default: "zh-CN") - 语言选项 (zh-CN, en-US)

**Authentication:** Required (JWT Token)

**Authorization:** User must have `sprint:view` permission or be project member

---

## Response Model

### SprintHealthResponse

```json
{
  "sprintId": 1,
  "sprintName": "Sprint 2024-01",
  "sprintStatus": "ACTIVE",
  "assessmentTime": "2024-01-15T10:30:00Z",
  "overallHealth": {
    "score": 75.5,
    "level": "GOOD",
    "trend": "IMPROVING"
  },
  "dimensions": [
    {
      "name": "burndownTrend",
      "displayName": "燃尽趋势",
      "score": 80.0,
      "status": "GOOD",
      "weight": 0.25,
      "details": {
        "idealRemaining": 45.0,
        "actualRemaining": 42.0,
        "deviation": -6.67,
        "trendSlope": -2.5
      },
      "description": "实际燃尽线略优于理想线，趋势良好"
    },
    {
      "name": "taskCompletion",
      "displayName": "任务完成率",
      "score": 70.0,
      "status": "MODERATE",
      "weight": 0.20,
      "details": {
        "totalTasks": 20,
        "completedTasks": 14,
        "completionRate": 70.0,
        "expectedCompletionRate": 75.0
      },
      "description": "完成率略低于预期进度"
    },
    {
      "name": "blockedTasks",
      "displayName": "阻塞任务",
      "score": 85.0,
      "status": "GOOD",
      "weight": 0.15,
      "details": {
        "totalTasks": 20,
        "blockedTasks": 2,
        "blockedRate": 10.0,
        "avgBlockedDays": 1.5
      },
      "description": "阻塞任务数量在可控范围内"
    },
    {
      "name": "worklogCompleteness",
      "displayName": "工作日志完整性",
      "score": 65.0,
      "status": "WARNING",
      "weight": 0.15,
      "details": {
        "expectedWorkdays": 60,
        "actualWorklogDays": 39,
        "completenessRate": 65.0,
        "missingDays": 21
      },
      "description": "工作日志记录不够完整，影响数据准确性"
    },
    {
      "name": "storyPointAccuracy",
      "displayName": "故事点估算准确度",
      "score": 75.0,
      "status": "GOOD",
      "weight": 0.15,
      "details": {
        "plannedPoints": 100,
        "completedPoints": 70,
        "estimatedCompletePoints": 95,
        "accuracyRate": 95.0,
        "avgPointDeviation": 5.0
      },
      "description": "故事点估算较为准确"
    },
    {
      "name": "velocityStability",
      "displayName": "团队速度稳定性",
      "score": 78.0,
      "status": "GOOD",
      "weight": 0.10,
      "details": {
        "currentVelocity": 70,
        "averageVelocity": 68,
        "velocityVariance": 12.5,
        "stabilityScore": 78.0
      },
      "description": "团队速度保持稳定"
    }
  ],
  "recommendations": [
    {
      "priority": "HIGH",
      "category": "WORKLOG",
      "title": "改善工作日志记录习惯",
      "description": "当前工作日志完整性仅65%，建议团队每日更新工作日志",
      "actionItems": [
        "在每日站会中提醒团队成员更新工作日志",
        "设置工作日志提醒通知",
        "将工作日志完整性纳入团队绩效指标"
      ],
      "expectedImpact": "提高数据准确性，更好地追踪项目进展"
    },
    {
      "priority": "MEDIUM",
      "category": "TASK_COMPLETION",
      "title": "加速任务完成进度",
      "description": "当前完成率70%低于预期75%，建议重新评估任务优先级",
      "actionItems": [
        "识别并移除低优先级任务",
        "为阻塞任务寻求帮助或升级",
        "考虑增加团队资源或延长Sprint"
      ],
      "expectedImpact": "提升Sprint目标达成率"
    }
  ],
  "historicalComparison": {
    "previousSprintScore": 72.0,
    "scoreChange": 3.5,
    "percentageChange": 4.86,
    "trend": "IMPROVING"
  },
  "riskAlerts": [
    {
      "severity": "MEDIUM",
      "category": "WORKLOG",
      "message": "工作日志完整性低于70%阈值",
      "detectedAt": "2024-01-15T10:30:00Z"
    }
  ]
}
```

---

## Health Score Calculation Logic

### Overall Score Formula

```
OverallScore = Σ(DimensionScore × Weight)
```

### Dimension Weights

| Dimension | Weight | Description |
|-----------|--------|-------------|
| Burndown Trend | 25% | 燃尽趋势健康度 |
| Task Completion | 20% | 任务完成率 |
| Blocked Tasks | 15% | 阻塞任务影响 |
| Worklog Completeness | 15% | 工作日志完整性 |
| Story Point Accuracy | 15% | 估算准确度 |
| Velocity Stability | 10% | 速度稳定性 |

### Health Levels

| Score Range | Level | Color | Description |
|-------------|-------|-------|-------------|
| 90-100 | EXCELLENT | Green | 健康状况优秀 |
| 75-89 | GOOD | Light Green | 健康状况良好 |
| 60-74 | MODERATE | Yellow | 需要关注 |
| 40-59 | WARNING | Orange | 存在风险 |
| 0-39 | CRITICAL | Red | 严重问题 |

---

## Dimension Calculation Details

### 1. Burndown Trend (燃尽趋势)

**Formula:**
```
Score = 100 - |deviation| × penalty_factor
deviation = (actual_remaining - ideal_remaining) / ideal_remaining × 100
penalty_factor = 2.0 (when ahead) or 1.5 (when behind)
```

**Data Source:**
- `burndown_points` table
- Compare actual vs ideal burndown line
- Calculate trend slope

**Thresholds:**
- Excellent: deviation < 5%
- Good: deviation 5-10%
- Moderate: deviation 10-20%
- Warning: deviation 20-30%
- Critical: deviation > 30%

### 2. Task Completion (任务完成率)

**Formula:**
```
Score = (actual_completion_rate / expected_completion_rate) × 100
expected_completion_rate = days_elapsed / total_days × 100
```

**Data Source:**
- `tasks` table (status = 'DONE')
- Sprint start/end dates

**Thresholds:**
- Excellent: actual >= expected + 10%
- Good: actual >= expected
- Moderate: actual >= expected - 10%
- Warning: actual >= expected - 20%
- Critical: actual < expected - 20%

### 3. Blocked Tasks (阻塞任务)

**Formula:**
```
Score = (1 - blocked_rate) × 100 - (avg_blocked_days × 5)
blocked_rate = blocked_tasks / total_tasks
```

**Data Source:**
- Custom `is_blocked` field in tasks
- `blocked_since` timestamp

**Thresholds:**
- Excellent: blocked_rate < 5%
- Good: blocked_rate 5-10%
- Moderate: blocked_rate 10-15%
- Warning: blocked_rate 15-25%
- Critical: blocked_rate > 25%

### 4. Worklog Completeness (工作日志完整性)

**Formula:**
```
Score = (actual_worklog_days / expected_workdays) × 100
expected_workdays = team_size × workdays_in_sprint
```

**Data Source:**
- `work_logs` table
- Count distinct (user_id, date) combinations

**Thresholds:**
- Excellent: completeness > 90%
- Good: completeness 80-90%
- Moderate: completeness 70-80%
- Warning: completeness 60-70%
- Critical: completeness < 60%

### 5. Story Point Accuracy (估算准确度)

**Formula:**
```
Score = 100 - avg_deviation
avg_deviation = Σ|estimated_points - actual_points| / completed_tasks
```

**Data Source:**
- `tasks` table (story_points vs actual completion time)
- Historical velocity data

**Thresholds:**
- Excellent: accuracy > 95%
- Good: accuracy 90-95%
- Moderate: accuracy 80-90%
- Warning: accuracy 70-80%
- Critical: accuracy < 70%

### 6. Velocity Stability (速度稳定性)

**Formula:**
```
Score = 100 - (velocity_coefficient_of_variation × 2)
CV = (standard_deviation / mean) × 100
```

**Data Source:**
- Last 3-5 sprints' velocity data
- Calculate mean and standard deviation

**Thresholds:**
- Excellent: CV < 10%
- Good: CV 10-15%
- Moderate: CV 15-25%
- Warning: CV 25-35%
- Critical: CV > 35%

---

## Recommendation Engine Logic

### Rule-Based Recommendations

1. **Worklog Completeness < 70%**
   - Priority: HIGH
   - Actions: Daily reminders, process improvement

2. **Blocked Tasks > 15%**
   - Priority: HIGH
   - Actions: Unblock sprint, escalate issues

3. **Completion Rate < Expected - 10%**
   - Priority: MEDIUM
   - Actions: Re-prioritize, add resources

4. **Burndown Deviation > 20%**
   - Priority: HIGH
   - Actions: Sprint adjustment, scope negotiation

5. **Velocity CV > 25%**
   - Priority: MEDIUM
   - Actions: Process stabilization, capacity planning

---

## Error Responses

### 404 Not Found
```json
{
  "error": "SPRINT_NOT_FOUND",
  "message": "Sprint with id 999 not found",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### 403 Forbidden
```json
{
  "error": "ACCESS_DENIED",
  "message": "User does not have permission to view this sprint",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### 400 Bad Request
```json
{
  "error": "INVALID_SPRINT_STATUS",
  "message": "Cannot assess health for PLANNED sprint",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

---

## Performance Considerations

- **Caching:** Cache assessment results for 1 hour (configurable)
- **Async Processing:** For historical analysis (optional background job)
- **Database Optimization:** Use indexed queries for aggregations
- **Rate Limiting:** Max 10 requests per minute per user

---

## Future Enhancements

1. **Machine Learning Predictions:** Predict sprint outcome based on current health
2. **Real-time Monitoring:** WebSocket updates for live health changes
3. **Custom Weights:** Allow users to configure dimension weights
4. **Team Comparison:** Compare health across multiple teams
5. **Automated Alerts:** Send notifications when health drops below threshold
6. **Historical Trends:** Long-term health trend analysis and visualization

---

## Testing Scenarios

### Test Case 1: Healthy Sprint
- All dimensions > 75%
- Expected: GOOD level, minimal recommendations

### Test Case 2: At-Risk Sprint
- Completion rate 55%, blocked tasks 20%
- Expected: WARNING level, HIGH priority recommendations

### Test Case 3: Critical Sprint
- Multiple dimensions < 40%
- Expected: CRITICAL level, immediate action required

### Test Case 4: Planned Sprint
- Status = PLANNED
- Expected: 400 error (cannot assess)

### Test Case 5: Insufficient Data
- Sprint just started (1 day)
- Expected: Partial assessment with disclaimer

---

## Implementation Notes

1. **Service Layer:** Create `SprintHealthService` with assessment logic
2. **DTOs:** Define `SprintHealthResponse`, `HealthDimension`, `Recommendation`
3. **Repository:** Add custom queries for aggregations
4. **Cache:** Use Redis for caching assessment results
5. **I18n:** Support Chinese and English recommendations
6. **Logging:** Log all assessments for analytics

---

## Database Schema Changes

### New Table: sprint_health_assessments (Optional - for history)

```sql
CREATE TABLE sprint_health_assessments (
    id BIGSERIAL PRIMARY KEY,
    sprint_id BIGINT NOT NULL,
    assessment_time TIMESTAMP NOT NULL,
    overall_score DECIMAL(5,2) NOT NULL,
    burndown_score DECIMAL(5,2),
    completion_score DECIMAL(5,2),
    blocked_score DECIMAL(5,2),
    worklog_score DECIMAL(5,2),
    accuracy_score DECIMAL(5,2),
    velocity_score DECIMAL(5,2),
    health_level VARCHAR(20),
    risk_count INT DEFAULT 0,
    recommendation_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (sprint_id) REFERENCES sprints(id)
);

CREATE INDEX idx_sprint_health_sprint ON sprint_health_assessments(sprint_id);
CREATE INDEX idx_sprint_health_time ON sprint_health_assessments(assessment_time);
```

### Add Fields to tasks Table (for blocked tracking)

```sql
ALTER TABLE tasks ADD COLUMN is_blocked BOOLEAN DEFAULT FALSE;
ALTER TABLE tasks ADD COLUMN blocked_since TIMESTAMP;
ALTER TABLE tasks ADD COLUMN blocked_reason VARCHAR(500);
```
