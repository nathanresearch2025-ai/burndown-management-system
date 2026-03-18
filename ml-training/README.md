# XGBoost 任务工时预测项目

## 📁 项目结构

```
ml-training/
├── generate_data.py          # 生成模拟训练数据
├── train_xgboost.py          # XGBoost 训练脚本
├── requirements.txt          # Python 依赖包
├── README.md                 # 本文件
├── training_data.csv         # 训练数据（运行后生成）
├── xgboost_model.pkl         # 训练好的模型（运行后生成）
├── label_encoders.pkl        # 特征编码器（运行后生成）
├── training_history.json     # 训练历史（运行后生成）
└── training_results.png      # 可视化结果（运行后生成）
```

## 🚀 快速开始

### 1. 安装依赖

```bash
pip install -r requirements.txt
```

### 2. 生成训练数据

```bash
python generate_data.py
```

这将生成 1000 条模拟任务数据，保存到 `training_data.csv`

### 3. 训练模型

```bash
python train_xgboost.py
```

训练完成后会生成：
- `xgboost_model.pkl` - 训练好的模型
- `label_encoders.pkl` - 特征编码器
- `training_history.json` - 训练历史和指标
- `training_results.png` - 可视化结果图

## 📊 在 Jupyter Notebook 中使用

### 方式 1：直接运行脚本

```python
# 在 Jupyter Notebook 的 Cell 中运行
%run train_xgboost.py
```

### 方式 2：导入模块使用

```python
# 导入训练脚本
from train_xgboost import TaskEffortPredictor

# 创建预测器
predictor = TaskEffortPredictor()

# 加载数据
df = predictor.load_data('training_data.csv')

# 训练模型
X, y = predictor.prepare_features(df, is_training=True)
X_test, y_test, y_pred = predictor.train(X, y)

# 查看特征重要性
predictor.get_feature_importance()

# 保存模型
predictor.save_model()
```

### 方式 3：加载已训练的模型进行预测

```python
from train_xgboost import TaskEffortPredictor

# 加载模型
predictor = TaskEffortPredictor()
predictor.load_model('xgboost_model.pkl', 'label_encoders.pkl')

# 预测新任务
new_task = {
    'task_type': 'STORY',
    'priority': 'HIGH',
    'story_points': 5,
    'title_length': 45,
    'description_length': 200,
    'labels_count': 2,
    'created_month': 3,
    'created_quarter': 1,
    'created_day_of_week': 2,
    'days_to_due': 14,
    'assignee_id': 3,
    'project_id': 1
}

predicted_hours = predictor.predict(new_task)
print(f"预测工时: {predicted_hours:.1f} 小时")
```

## 📈 数据特征说明

### 输入特征（12 个）

| 特征名 | 类型 | 说明 | 取值范围 |
|--------|------|------|----------|
| task_type | 类别 | 任务类型 | STORY, BUG, TASK, EPIC |
| priority | 类别 | 优先级 | CRITICAL, HIGH, MEDIUM, LOW |
| story_points | 数值 | 故事点 | 1, 2, 3, 5, 8, 13 |
| title_length | 数值 | 标题长度 | 10-100 |
| description_length | 数值 | 描述长度 | 0-500 |
| labels_count | 数值 | 标签数量 | 0-5 |
| created_month | 数值 | 创建月份 | 1-12 |
| created_quarter | 数值 | 创建季度 | 1-4 |
| created_day_of_week | 数值 | 创建星期 | 0-6 |
| days_to_due | 数值 | 距截止日期 | 1-90 |
| assignee_id | 数值 | 负责人 ID | 1-10 |
| project_id | 数值 | 项目 ID | 1-5 |

### 目标变量

- **actual_hours**: 实际工时（小时），范围 1-100

## 🎯 模型性能目标

- **MAE（平均绝对误差）**: < 5 小时（优秀），< 10 小时（良好）
- **R²（决定系数）**: > 0.7（优秀），> 0.5（良好）
- **预测准确率（±20%）**: > 80%

## 🔧 模型参数

```python
{
    'objective': 'reg:squarederror',  # 回归任务
    'max_depth': 6,                   # 树的最大深度
    'learning_rate': 0.1,             # 学习率
    'n_estimators': 200,              # 树的数量
    'subsample': 0.8,                 # 样本采样比例
    'colsample_bytree': 0.8,          # 特征采样比例
    'random_state': 42                # 随机种子
}
```

## 📝 使用示例

### 完整的训练流程

```python
# 1. 生成数据
%run generate_data.py

# 2. 训练模型
%run train_xgboost.py

# 3. 查看结果
from IPython.display import Image
Image('training_results.png')
```

### 批量预测

```python
import pandas as pd
from train_xgboost import TaskEffortPredictor

# 加载模型
predictor = TaskEffortPredictor()
predictor.load_model('xgboost_model.pkl', 'label_encoders.pkl')

# 批量预测
new_tasks = pd.DataFrame([
    {'task_type': 'STORY', 'priority': 'HIGH', 'story_points': 5, ...},
    {'task_type': 'BUG', 'priority': 'CRITICAL', 'story_points': 3, ...},
    {'task_type': 'TASK', 'priority': 'MEDIUM', 'story_points': 8, ...}
])

predictions = predictor.predict(new_tasks)
print(predictions)
```

## 🐛 常见问题

### Q1: 提示缺少依赖包

```bash
# 安装所有依赖
pip install -r requirements.txt
```

### Q2: 在 Jupyter 中运行脚本报错

确保在正确的目录下运行：

```python
import os
os.chdir('D:/java/claude/projects/2/ml-training')
%run train_xgboost.py
```

### Q3: 中文显示乱码

在 Jupyter 中添加：

```python
import matplotlib.pyplot as plt
plt.rcParams['font.sans-serif'] = ['SimHei']  # 用来正常显示中文标签
plt.rcParams['axes.unicode_minus'] = False    # 用来正常显示负号
```

## 📚 下一步

1. **优化模型**: 使用 GridSearchCV 或 Optuna 进行超参数调优
2. **特征工程**: 添加更多特征（如历史平均工时、团队速度等）
3. **集成到后端**: 将模型部署为 Flask API，供 Spring Boot 调用
4. **实时训练**: 定期使用新数据重新训练模型

## 📞 支持

如有问题，请查看：
- XGBoost 文档: https://xgboost.readthedocs.io/
- Scikit-learn 文档: https://scikit-learn.org/
