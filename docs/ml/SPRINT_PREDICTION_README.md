# Sprint 完成预测功能实现

## 功能概述

基于随机森林机器学习模型的 Sprint 完成预测系统，能够分析当前 Sprint 状态并预测完成概率。

## 实现架构

### 1. 核心组件

- **SprintPredictionService** - 主要预测服务，负责特征计算和预测逻辑
- **PythonModelService** - Python 模型调用服务，通过 ProcessBuilder 执行推理
- **SprintPredictionController** - REST API 控制器
- **SprintCompletionPredictionDto** - 预测结果数据传输对象

### 2. 模型文件

- `src/main/resources/models/random_forest_model.pkl` - 训练好的随机森林模型
- `src/main/resources/models/feature_columns.json` - 特征列配置

### 3. API 端点

```
GET /api/v1/sprints/{id}/completion-probability
```

**响应示例：**
```json
{
  "probability": 0.9608,
  "riskLevel": "GREEN",
  "featureSummary": {
    "daysElapsedRatio": 0.5,
    "remainingRatio": 0.417,
    "velocityCurrent": 5.0,
    "velocityAvg": 4.8,
    "projectedCompletionRatio": 1.17,
    "blockedStories": 1,
    "attendanceRate": 0.9
  },
  "predictedAt": 1710141649000
}
```

## 特征工程

### 基础特征（13个）
- sprint_days, days_elapsed, committed_sp, remaining_sp, completed_sp
- velocity_current, velocity_avg_5, velocity_std_5
- blocked_stories, attendance_rate
- ratio_feature, ratio_bug, ratio_tech_debt

### 衍生特征（6个）
- days_remaining, elapsed_ratio, remaining_ratio
- velocity_gap, projected_sp, projected_completion_ratio

## 风险等级映射

- **GREEN**: probability >= 0.8 (高概率完成)
- **YELLOW**: 0.5 <= probability < 0.8 (中等风险)
- **RED**: probability < 0.5 (高风险)

## 测试方法

### 1. 使用 Postman 集合
导入 `Sprint_Prediction_API.postman_collection.json` 文件，包含：
- 用户登录认证
- Sprint 管理接口
- 预测接口测试

### 2. 测试流程
1. 执行 Login 请求获取 JWT Token
2. 执行 Get All Sprints 获取 Sprint 列表
3. 执行 Predict Sprint Completion 进行预测

### 3. Python 环境要求
```bash
pip install scikit-learn pandas numpy joblib
```

## 部署注意事项

1. **Python 环境** - 确保服务器安装 Python 3.x 和所需依赖
2. **模型文件** - 确保模型文件正确复制到 resources/models 目录
3. **权限配置** - 预测接口需要 SPRINT_READ 权限
4. **性能优化** - 考虑缓存预测结果，避免频繁调用

## 监控指标

建议添加以下监控指标：
- 预测请求数量和响应时间
- Python 进程调用成功率
- 模型预测准确性跟踪

## 未来改进

1. **模型优化** - 定期重训练模型，提高预测准确性
2. **缓存机制** - 添加 Redis 缓存减少重复计算
3. **批量预测** - 支持多个 Sprint 批量预测
4. **实时更新** - 基于任务状态变化实时更新预测
