# Sprint Health Assessment - Implementation Guide

## 概述

Sprint健康度评估功能已成功实现，包含完整的后端服务、API端点和国际化支持。

## 已实现的文件

### 后端代码

1. **DTO层**
   - `SprintHealthResponse.java` - 健康度评估响应DTO，包含所有嵌套类型定义

2. **Service层**
   - `SprintHealthService.java` - 核心业务逻辑，实现6个维度的健康度计算

3. **Controller层**
   - `SprintController.java` - 添加了 `/sprints/{id}/health` 端点

4. **国际化**
   - `messages_zh_CN.properties` - 中文消息
   - `messages_en_US.properties` - 英文消息

## API 使用说明

### 端点信息

```
GET /api/v1/sprints/{sprintId}/health
```

### 请求参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| sprintId | Long | 是 | - | Sprint ID (路径参数) |
| includeRecommendations | Boolean | 否 | true | 是否包含改进建议 |
| lang | String | 否 | zh-CN | 语言选项 (zh-CN, en-US) |

### 请求示例

#### 示例1：获取完整健康度报告（中文）

```bash
curl -X GET "http://localhost:8080/api/v1/sprints/1/health?includeRecommendations=true&lang=zh-CN" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json"
```

#### 示例2：获取健康度报告（英文，不含建议）

```bash
curl -X GET "http://localhost:8080/api/v1/sprints/1/health?includeRecommendations=false&lang=en-US" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json"
```

#### 示例3：使用默认参数

```bash
curl -X GET "http://localhost:8080/api/v1/sprints/1/health" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json"
```

### 响应示例

```json
{
  "sprintId": 1,
  "sprintName": "Sprint 2024-01",
  "sprintStatus": "ACTIVE",
  "assessmentTime": "2024-01-15T10:30:00",
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
      "detectedAt": "2024-01-15T10:30:00"
    }
  ]
}
```

## 健康度计算逻辑

### 6个评估维度

| 维度 | 权重 | 计算依据 |
|------|------|----------|
| 燃尽趋势 (burndownTrend) | 25% | 实际vs理想燃尽线偏差 |
| 任务完成率 (taskCompletion) | 20% | 实际完成率vs预期完成率 |
| 阻塞任务 (blockedTasks) | 15% | 阻塞任务占比 |
| 工作日志完整性 (worklogCompleteness) | 15% | 实际工作日志天数vs预期天数 |
| 故事点准确度 (storyPointAccuracy) | 15% | 估算故事点vs实际完成 |
| 速度稳定性 (velocityStability) | 10% | 历史速度的变异系数 |

### 健康等级划分

| 分数范围 | 等级 | 说明 |
|----------|------|------|
| 90-100 | EXCELLENT | 健康状况优秀 |
| 75-89 | GOOD | 健康状况良好 |
| 60-74 | MODERATE | 需要关注 |
| 40-59 | WARNING | 存在风险 |
| 0-39 | CRITICAL | 严重问题 |

## 缓存配置

该API使用了Spring Cache，缓存配置：
- 缓存名称：`sprintHealth`
- 缓存键：`sprintId`
- 缓存时间：1小时（在CacheConfig中配置）

要刷新缓存，可以重启应用或调用缓存清除接口（需要额外实现）。

## 错误处理

### 404 Not Found - Sprint不存在

```json
{
  "error": "SPRINT_NOT_FOUND",
  "message": "Sprint with id 999 not found",
  "timestamp": "2024-01-15T10:30:00"
}
```

### 400 Bad Request - Sprint状态不合法

```json
{
  "error": "INVALID_SPRINT_STATUS",
  "message": "Cannot assess health for PLANNED sprint",
  "timestamp": "2024-01-15T10:30:00"
}
```

### 403 Forbidden - 权限不足

```json
{
  "error": "ACCESS_DENIED",
  "message": "User does not have permission to view this sprint",
  "timestamp": "2024-01-15T10:30:00"
}
```

## 前端集成建议

### 1. 创建API客户端

在 `frontend/src/api/sprint.ts` 中添加：

```typescript
export interface SprintHealthResponse {
  sprintId: number;
  sprintName: string;
  sprintStatus: string;
  assessmentTime: string;
  overallHealth: {
    score: number;
    level: 'EXCELLENT' | 'GOOD' | 'MODERATE' | 'WARNING' | 'CRITICAL';
    trend: 'IMPROVING' | 'STABLE' | 'DECLINING';
  };
  dimensions: HealthDimension[];
  recommendations: Recommendation[];
  historicalComparison: HistoricalComparison;
  riskAlerts: RiskAlert[];
}

export interface HealthDimension {
  name: string;
  displayName: string;
  score: number;
  status: string;
  weight: number;
  details: any;
  description: string;
}

export interface Recommendation {
  priority: 'HIGH' | 'MEDIUM' | 'LOW';
  category: string;
  title: string;
  description: string;
  actionItems: string[];
  expectedImpact: string;
}

export const getSprintHealth = async (
  sprintId: number,
  includeRecommendations: boolean = true,
  lang: string = 'zh-CN'
): Promise<SprintHealthResponse> => {
  const response = await apiClient.get(
    `/sprints/${sprintId}/health`,
    { params: { includeRecommendations, lang } }
  );
  return response.data;
};
```

### 2. 使用React Query

```typescript
import { useQuery } from '@tanstack/react-query';
import { getSprintHealth } from '@/api/sprint';

export const useSprintHealth = (sprintId: number) => {
  return useQuery({
    queryKey: ['sprintHealth', sprintId],
    queryFn: () => getSprintHealth(sprintId),
    staleTime: 1000 * 60 * 5, // 5分钟
    enabled: !!sprintId,
  });
};
```

### 3. UI组件示例

```tsx
import { useSprintHealth } from '@/hooks/useSprintHealth';
import { Progress, Card, Tag, Alert } from 'antd';

export const SprintHealthCard: React.FC<{ sprintId: number }> = ({ sprintId }) => {
  const { data: health, isLoading } = useSprintHealth(sprintId);

  if (isLoading) return <Card loading />;
  if (!health) return null;

  const getHealthColor = (level: string) => {
    const colors = {
      EXCELLENT: 'success',
      GOOD: 'success',
      MODERATE: 'warning',
      WARNING: 'error',
      CRITICAL: 'error',
    };
    return colors[level] || 'default';
  };

  return (
    <Card title="Sprint健康度评估">
      <div style={{ marginBottom: 24 }}>
        <Progress
          type="circle"
          percent={health.overallHealth.score}
          format={(percent) => `${percent?.toFixed(1)}`}
          status={health.overallHealth.level === 'CRITICAL' ? 'exception' : 'normal'}
        />
        <Tag color={getHealthColor(health.overallHealth.level)}>
          {health.overallHealth.level}
        </Tag>
        <Tag>{health.overallHealth.trend}</Tag>
      </div>

      {health.riskAlerts.length > 0 && (
        <Alert
          type="warning"
          message="风险警报"
          description={
            <ul>
              {health.riskAlerts.map((alert, idx) => (
                <li key={idx}>{alert.message}</li>
              ))}
            </ul>
          }
          style={{ marginBottom: 16 }}
        />
      )}

      <div>
        {health.dimensions.map((dim) => (
          <div key={dim.name} style={{ marginBottom: 16 }}>
            <div>{dim.displayName}</div>
            <Progress percent={dim.score} size="small" />
            <div style={{ fontSize: 12, color: '#666' }}>{dim.description}</div>
          </div>
        ))}
      </div>

      {health.recommendations.length > 0 && (
        <Card type="inner" title="改进建议" style={{ marginTop: 16 }}>
          {health.recommendations.map((rec, idx) => (
            <div key={idx} style={{ marginBottom: 16 }}>
              <Tag color={rec.priority === 'HIGH' ? 'red' : 'orange'}>
                {rec.priority}
              </Tag>
              <strong>{rec.title}</strong>
              <p>{rec.description}</p>
              <ul>
                {rec.actionItems.map((item, i) => (
                  <li key={i}>{item}</li>
                ))}
              </ul>
            </div>
          ))}
        </Card>
      )}
    </Card>
  );
};
```

## 测试步骤

### 1. 准备测试数据

确保数据库中有：
- 至少一个Project
- 至少一个ACTIVE或COMPLETED状态的Sprint
- Sprint中有多个Task（不同状态）
- 有WorkLog记录
- 有BurndownPoint记录

### 2. 启动后端服务

```bash
cd backend
mvn spring-boot:run
```

### 3. 获取JWT Token

```bash
curl -X POST "http://localhost:8080/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }'
```

### 4. 调用健康度评估API

```bash
# 替换YOUR_JWT_TOKEN为实际token
curl -X GET "http://localhost:8080/api/v1/sprints/1/health" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json"
```

### 5. 验证响应

检查响应中的：
- ✅ overallHealth.score 在 0-100 之间
- ✅ 包含6个dimensions
- ✅ 每个dimension都有score和description
- ✅ recommendations数组（如果有低于70分的维度）
- ✅ riskAlerts数组（如果有低于70分的维度）

## Swagger文档

启动应用后，访问：
```
http://localhost:8080/api/v1/swagger-ui.html
```

在Swagger UI中可以直接测试该API。

## 性能优化建议

1. **数据库索引**
   - 确保 `tasks.sprint_id` 有索引
   - 确保 `work_logs.sprint_id` 有索引
   - 确保 `burndown_points.sprint_id` 有索引

2. **缓存策略**
   - 已实现Redis缓存，缓存时间1小时
   - 高频访问的Sprint可以预热缓存

3. **异步计算**
   - 对于历史数据分析，可以考虑后台任务
   - 使用消息队列处理大量Sprint的批量评估

## 未来增强方向

1. **机器学习预测**
   - 基于当前健康度预测Sprint成功率
   - 预测Sprint结束时的最终得分

2. **实时监控**
   - WebSocket推送健康度变化
   - 当健康度下降时自动告警

3. **自定义权重**
   - 允许用户配置各维度的权重
   - 不同项目类型使用不同的权重模板

4. **历史趋势图**
   - 展示Sprint健康度的历史变化
   - 对比多个Sprint的健康度

5. **团队对比**
   - 对比不同团队的Sprint健康度
   - 识别最佳实践和问题模式

## 故障排除

### 问题1：返回404错误

**原因：** Sprint不存在或ID错误

**解决：** 检查数据库中是否存在该Sprint

```sql
SELECT * FROM sprints WHERE id = 1;
```

### 问题2：返回400错误 (PLANNED sprint)

**原因：** Sprint状态为PLANNED，无法评估

**解决：** 先启动Sprint

```bash
curl -X POST "http://localhost:8080/api/v1/sprints/1/start" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### 问题3：某些维度得分为0

**原因：** 缺少相关数据（如BurndownPoint、WorkLog）

**解决：** 确保Sprint有完整的数据记录

### 问题4：国际化消息显示为key

**原因：** 消息文件未正确加载

**解决：** 检查 `messages_zh_CN.properties` 和 `messages_en_US.properties` 是否在classpath中

## 总结

Sprint健康度评估API已完全实现，具备：
- ✅ 完整的6维度健康度计算
- ✅ 智能的改进建议生成
- ✅ 风险警报机制
- ✅ 中英文国际化支持
- ✅ Redis缓存优化
- ✅ Swagger API文档
- ✅ 完善的错误处理

该功能可以帮助Scrum Master和项目经理：
1. 快速识别Sprint风险
2. 获得数据驱动的改进建议
3. 对比历史Sprint表现
4. 提高团队交付质量

持续监控，一步步显示。
