# Sprint 完成概率预测功能

## 功能概述

在项目列表页面中，每个项目都可以查看其当前活跃 Sprint 的完成概率预测。该功能通过分析多个特征来预测 Sprint 是否能按时完成。

## 使用方法

### 1. 在项目列表中查看预测

1. 进入项目列表页面 (`/projects`)
2. 找到目标项目行
3. 点击"查看预测"按钮（图表图标）
4. 系统会拉出右侧抽屉，显示预测数据

### 2. 预测数据说明

#### 完成概率
- **数值范围**: 0-100%
- **含义**: 根据当前进度预测 Sprint 按时完成的可能性

#### 风险等级
- **绿色（低风险）**: Sprint 进展良好，预计能够按时完成
- **黄色（中风险）**: Sprint 存在一定风险，需要关注
- **红色（高风险）**: Sprint 很可能无法按时完成，需要采取措施

#### 特征摘要

预测模型考虑以下特征：

1. **时间进度 (Days Elapsed Ratio)**
   - Sprint 已经过去的时间占总时间的比例

2. **剩余工作占比 (Remaining Ratio)**
   - 还有多少工作未完成（基于故事点）

3. **当前速度 (Velocity Current)**
   - 最近几天团队的开发速度

4. **平均速度 (Velocity Avg)**
   - Sprint 整体的平均开发速度

5. **预计完成率 (Projected Completion Ratio)**
   - 根据当前速度推算，到截止日期能完成多少工作

6. **阻塞任务数 (Blocked Stories)**
   - 当前被阻塞的任务数量

7. **出勤率 (Attendance Rate)**
   - 团队成员的平均出勤率

## API 端点

```
GET /api/v1/sprints/{sprintId}/completion-probability
```

**响应示例:**
```json
{
  "probability": 0.85,
  "riskLevel": "GREEN",
  "featureSummary": {
    "daysElapsedRatio": 0.6,
    "remainingRatio": 0.3,
    "velocityCurrent": 15.5,
    "velocityAvg": 14.2,
    "projectedCompletionRatio": 0.92,
    "blockedStories": 1,
    "attendanceRate": 0.95
  },
  "predictedAt": 1695276000000
}
```

## 前端实现

### 核心组件
- **ProjectList.tsx**: 项目列表页面，包含预测按钮和抽屉组件
- **sprint.ts**: API 接口定义

### 状态管理
- `predictionDrawerVisible`: 控制抽屉显示/隐藏
- `predictionData`: 存储预测结果
- `predictionLoading`: 加载状态

### 用户交互流程
1. 点击"查看预测"按钮
2. 显示右侧抽屉
3. 自动获取项目的活跃 Sprint
4. 调用预测 API
5. 展示预测结果（卡片形式）
6. 点击外部或关闭按钮收起抽屉

## 注意事项

1. **仅活跃 Sprint**: 只能预测状态为 ACTIVE 的 Sprint
2. **无活跃 Sprint**: 如果项目没有活跃 Sprint，会显示提示信息
3. **数据刷新**: 每次打开抽屉都会重新获取最新预测数据
4. **国际化支持**: 所有文本支持中英文切换

## 后续优化建议

1. 添加历史预测记录，展示预测准确度
2. 支持预测多个 Sprint
3. 添加更多可视化图表（趋势图、对比图）
4. 支持导出预测报告
5. 添加预测数据缓存，减少 API 调用
