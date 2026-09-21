# 任务工时预测技术文档

## 1. 业务场景

根据任务标题、类型、优先级、故事点等信息，预测该任务实际需要的工时（小时），帮助团队更准确地进行容量规划。

**核心价值：**
- 提升 Sprint 规划准确性
- 减少工时估算偏差
- 优化资源分配决策
- 识别高风险任务

---

## 2. 数据源分析

### 2.1 核心数据表

**tasks 表（任务属性）**
```java
// 位置：backend/src/main/java/com/burndown/entity/Task.java

关键字段：
- id: Long                          // 任务 ID
- projectId: Long                   // 项目 ID
- sprintId: Long                    // Sprint ID
- taskKey: String                   // 任务编号（如 TASK-123）
- title: String                     // 任务标题（最长 500 字符）
- description: String               // 任务描述（TEXT 类型）
- type: TaskType                    // 任务类型（STORY/BUG/TASK/EPIC）
- status: TaskStatus                // 状态（TODO/IN_PROGRESS/IN_REVIEW/DONE/BLOCKED）
- priority: TaskPriority            // 优先级（CRITICAL/HIGH/MEDIUM/LOW）
- storyPoints: BigDecimal           // 故事点（精度 5,1）
- originalEstimate: BigDecimal      // 原始估算工时（精度 10,2）
- remainingEstimate: BigDecimal     // 剩余估算工时
- timeSpent: BigDecimal             // 实际花费工时（目标变量）
- assigneeId: Long                  // 负责人 ID
- reporterId: Long                  // 报告人 ID
- labels: String[]                  // 标签数组
- customFields: String (JSONB)      // 自定义字段
- embedding: PGvector(1536)         // 向量嵌入（用于语义相似度）
- createdAt: LocalDateTime          // 创建时间
- updatedAt: LocalDateTime          // 更新时间
- resolvedAt: LocalDateTime         // 解决时间
- dueDate: LocalDate                // 截止日期
```

**work_logs 表（实际工时记录）**
```java
// 位置：backend/src/main/java/com/burndown/entity/WorkLog.java

关键字段：
- id: Long                          // 工作日志 ID
- taskId: Long                      // 关联任务 ID
- userId: Long                      // 用户 ID
- workDate: LocalDate               // 工作日期
- timeSpent: BigDecimal             // 实际花费时间（小时，精度 10,2）
- remainingEstimate: BigDecimal     // 剩余估算
- comment: String                   // 工作备注
- createdAt: LocalDateTime          // 创建时间
- updatedAt: LocalDateTime          // 更新时间

约束：
- UNIQUE(task_id, user_id, work_date)  // 同一用户同一天对同一任务只能有一条记录
```

### 2.2 数据聚合逻辑

**目标变量（y）：** 任务总工时
```sql
-- 从 work_logs 聚合得到每个任务的实际总工时
SELECT
    task_id,
    SUM(time_spent) as total_time_spent,
    COUNT(DISTINCT user_id) as contributor_count,
    COUNT(*) as log_count,
    MAX(work_date) - MIN(work_date) as duration_days
FROM work_logs
GROUP BY task_id;
```

**特征数据（X）：** 从 tasks 表提取

---

## 3. 算法选择：XGBoost vs 神经网络

### 3.1 XGBoost（推荐）

**优势：**
- ✅ **表格数据性能优异**：在结构化数据上通常优于神经网络
- ✅ **可解释性强**：可输出特征重要性，便于业务理解
- ✅ **训练速度快**：小数据集（< 10 万条）训练时间短
- ✅ **鲁棒性好**：对缺失值、异常值容忍度高
- ✅ **无需大量数据**：1000+ 样本即可训练有效模型
- ✅ **超参数调优简单**：参数少且直观

**劣势：**
- ❌ 无法处理文本语义（需要手工特征工程）
- ❌ 对高维稀疏特征支持一般

**适用场景：**
- 数据量：1000 - 100 万条
- 特征类型：数值型、类别型为主
- 业务需求：需要模型可解释性
- 团队技能：Python 基础即可

**Python 库选择：**
```python
# 推荐：XGBoost（最成熟）
import xgboost as xgb

# 备选：LightGBM（更快，内存占用更小）
import lightgbm as lgb

# 备选：CatBoost（对类别特征支持最好）
import catboost as cb
```

---

### 3.2 神经网络

**优势：**
- ✅ **文本语义理解**：可结合 Transformer 处理标题/描述
- ✅ **非线性拟合能力强**：复杂交互特征自动学习
- ✅ **可扩展性好**：数据量增大时性能持续提升

**劣势：**
- ❌ **需要大量数据**：通常需要 10 万+ 样本
- ❌ **训练时间长**：需要 GPU 加速
- ❌ **可解释性差**：黑盒模型，难以解释预测原因
- ❌ **超参数调优复杂**：网络结构、学习率、正则化等
- ❌ **过拟合风险高**：小数据集容易过拟合

**适用场景：**
- 数据量：10 万+ 条
- 特征类型：包含大量文本、图像等非结构化数据
- 业务需求：追求极致精度，可接受黑盒
- 团队技能：深度学习经验

**Python 库选择：**
```python
# 推荐：PyTorch（灵活性高）
import torch
import torch.nn as nn

# 备选：TensorFlow/Keras（易用性好）
import tensorflow as tf
from tensorflow import keras

# 备选：FastAI（高层封装，快速原型）
from fastai.tabular.all import *
```

---

## 4. 推荐方案：XGBoost + 文本特征工程

### 4.1 为什么选择 XGBoost？

基于当前项目特点：
1. **数据量预估**：初期可能只有 1000-5000 条任务记录
2. **特征类型**：80% 是结构化数据（类型、优先级、故事点等）
3. **业务需求**：需要解释"为什么预测这个工时"
4. **团队能力**：Spring Boot 后端团队，Python 基础即可

**结论：XGBoost 是最佳起点，后续数据量增大后可考虑神经网络。**

---

## 5. 特征工程设计

### 5.1 基础特征（直接从 tasks 表提取）

```python
# 1. 类别特征（需要编码）
categorical_features = [
    'type',           # STORY/BUG/TASK/EPIC
    'priority',       # CRITICAL/HIGH/MEDIUM/LOW
    'status',         # TODO/IN_PROGRESS/IN_REVIEW/DONE/BLOCKED
]

# 2. 数值特征
numerical_features = [
    'story_points',           # 故事点
    'original_estimate',      # 原始估算工时
    'title_length',           # 标题长度
    'description_length',     # 描述长度
    'labels_count',           # 标签数量
]

# 3. 时间特征
time_features = [
    'created_month',          # 创建月份（1-12）
    'created_quarter',        # 创建季度（1-4）
    'created_day_of_week',    # 创建星期几（0-6）
    'days_to_due',            # 距离截止日期天数
]
```

### 5.2 文本特征（从 title 和 description 提取）

```python
# 方案 1：统计特征（简单）
text_features = [
    'title_word_count',       # 标题词数
    'title_char_count',       # 标题字符数
    'description_word_count', # 描述词数
    'description_char_count', # 描述字符数
    'has_description',        # 是否有描述（0/1）
]

# 方案 2：TF-IDF 特征（中等）
from sklearn.feature_extraction.text import TfidfVectorizer

tfidf = TfidfVectorizer(max_features=100, ngram_range=(1, 2))
title_tfidf = tfidf.fit_transform(tasks['title'])

# 方案 3：语义嵌入（高级，利用现有 embedding 字段）
# tasks 表已有 embedding 字段（1536 维向量）
# 可直接使用或降维后使用
from sklearn.decomposition import PCA

pca = PCA(n_components=50)
title_embedding_reduced = pca.fit_transform(embeddings)
```

### 5.3 历史统计特征（从 work_logs 聚合）

```python
# 相似任务的历史工时统计
similar_task_features = [
    'same_type_avg_time',         # 同类型任务平均工时
    'same_type_std_time',         # 同类型任务工时标准差
    'same_priority_avg_time',     # 同优先级任务平均工时
    'same_assignee_avg_time',     # 同负责人历史平均工时
    'same_project_avg_time',      # 同项目历史平均工时
]

# SQL 示例：计算同类型任务平均工时
SELECT
    t.type,
    AVG(wl.total_time) as avg_time,
    STDDEV(wl.total_time) as std_time
FROM tasks t
JOIN (
    SELECT task_id, SUM(time_spent) as total_time
    FROM work_logs
    GROUP BY task_id
) wl ON t.id = wl.task_id
GROUP BY t.type;
```

### 5.4 团队特征

```python
team_features = [
    'assignee_avg_velocity',      # 负责人历史平均速度（工时/故事点）
    'assignee_task_count',        # 负责人当前任务数
    'team_size',                  # 团队规模
    'sprint_capacity_used',       # Sprint 容量使用率
]
```

---

## 6. Python 实现方案

### 6.1 技术栈选择

```python
# 核心库
xgboost==2.0.3              # XGBoost 模型
scikit-learn==1.4.0         # 特征工程、评估
pandas==2.2.0               # 数据处理
numpy==1.26.3               # 数值计算

# 数据库连接
psycopg2-binary==2.9.9      # PostgreSQL 驱动
sqlalchemy==2.0.25          # ORM

# 可视化
matplotlib==3.8.2           # 绘图
seaborn==0.13.1             # 统计图表
shap==0.44.0                # 模型解释

# 实验管理（可选）
mlflow==2.10.0              # 模型版本管理
optuna==3.5.0               # 超参数优化
```

### 6.2 项目结构

```
backend/ml-models/
├── data/
│   ├── raw/                    # 原始数据
│   ├── processed/              # 处理后数据
│   └── features/               # 特征工程结果
├── models/
│   ├── xgboost_v1.pkl         # 训练好的模型
│   ├── feature_encoder.pkl    # 特征编码器
│   └── scaler.pkl             # 数据标准化器
├── notebooks/
│   ├── 01_eda.ipynb           # 探索性数据分析
│   ├── 02_feature_engineering.ipynb
│   └── 03_model_training.ipynb
├── src/
│   ├── data_loader.py         # 数据加载
│   ├── feature_engineering.py # 特征工程
│   ├── model_trainer.py       # 模型训练
│   ├── model_evaluator.py     # 模型评估
│   └── predictor.py           # 预测服务
├── tests/
│   └── test_predictor.py
├── requirements.txt
└── README.md
```

### 6.3 核心代码示例

**数据加载（data_loader.py）**
```python
import pandas as pd
from sqlalchemy import create_engine

class TaskDataLoader:
    def __init__(self, db_url):
        self.engine = create_engine(db_url)

    def load_training_data(self):
        """加载训练数据：任务 + 实际工时"""
        query = """
        SELECT
            t.id,
            t.project_id,
            t.sprint_id,
            t.task_key,
            t.title,
            t.description,
            t.type,
            t.priority,
            t.story_points,
            t.original_estimate,
            t.assignee_id,
            t.reporter_id,
            t.labels,
            t.created_at,
            t.due_date,
            t.resolved_at,
            COALESCE(wl.total_time_spent, 0) as actual_time_spent,
            wl.contributor_count,
            wl.log_count
        FROM tasks t
        LEFT JOIN (
            SELECT
                task_id,
                SUM(time_spent) as total_time_spent,
                COUNT(DISTINCT user_id) as contributor_count,
                COUNT(*) as log_count
            FROM work_logs
            GROUP BY task_id
        ) wl ON t.id = wl.task_id
        WHERE t.status = 'DONE'  -- 只使用已完成的任务
          AND wl.total_time_spent > 0  -- 有实际工时记录
          AND wl.total_time_spent < 200  -- 过滤异常值
        """
        return pd.read_sql(query, self.engine)
```

**特征工程（feature_engineering.py）**
```python
import numpy as np
from sklearn.preprocessing import LabelEncoder, StandardScaler

class TaskFeatureEngineering:
    def __init__(self):
        self.label_encoders = {}
        self.scaler = StandardScaler()

    def create_features(self, df):
        """创建所有特征"""
        df = df.copy()

        # 1. 文本长度特征
        df['title_length'] = df['title'].str.len()
        df['description_length'] = df['description'].fillna('').str.len()
        df['has_description'] = (df['description_length'] > 0).astype(int)

        # 2. 时间特征
        df['created_month'] = pd.to_datetime(df['created_at']).dt.month
        df['created_quarter'] = pd.to_datetime(df['created_at']).dt.quarter
        df['created_day_of_week'] = pd.to_datetime(df['created_at']).dt.dayofweek

        # 3. 截止日期压力
        df['days_to_due'] = (
            pd.to_datetime(df['due_date']) - pd.to_datetime(df['created_at'])
        ).dt.days
        df['days_to_due'] = df['days_to_due'].fillna(30)  # 默认 30 天

        # 4. 标签数量
        df['labels_count'] = df['labels'].apply(
            lambda x: len(x) if isinstance(x, list) else 0
        )

        # 5. 类别编码
        categorical_cols = ['type', 'priority']
        for col in categorical_cols:
            if col not in self.label_encoders:
                self.label_encoders[col] = LabelEncoder()
                df[f'{col}_encoded'] = self.label_encoders[col].fit_transform(df[col])
            else:
                df[f'{col}_encoded'] = self.label_encoders[col].transform(df[col])

        return df

    def add_historical_features(self, df):
        """添加历史统计特征"""
        # 同类型任务平均工时
        type_stats = df.groupby('type')['actual_time_spent'].agg(['mean', 'std']).reset_index()
        type_stats.columns = ['type', 'same_type_avg_time', 'same_type_std_time']
        df = df.merge(type_stats, on='type', how='left')

        # 同优先级任务平均工时
        priority_stats = df.groupby('priority')['actual_time_spent'].agg(['mean']).reset_index()
        priority_stats.columns = ['priority', 'same_priority_avg_time']
        df = df.merge(priority_stats, on='priority', how='left')

        return df
```

**模型训练（model_trainer.py）**
```python
import xgboost as xgb
from sklearn.model_selection import train_test_split, cross_val_score
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
import joblib

class XGBoostTrainer:
    def __init__(self):
        self.model = None
        self.feature_names = None

    def train(self, X, y, params=None):
        """训练 XGBoost 模型"""
        # 默认参数
        if params is None:
            params = {
                'objective': 'reg:squarederror',
                'max_depth': 6,
                'learning_rate': 0.1,
                'n_estimators': 200,
                'subsample': 0.8,
                'colsample_bytree': 0.8,
                'random_state': 42
            }

        # 划分训练集和验证集
        X_train, X_val, y_train, y_val = train_test_split(
            X, y, test_size=0.2, random_state=42
        )

        # 训练模型
        self.model = xgb.XGBRegressor(**params)
        self.model.fit(
            X_train, y_train,
            eval_set=[(X_val, y_val)],
            early_stopping_rounds=20,
            verbose=10
        )

        self.feature_names = X.columns.tolist()

        # 评估
        y_pred = self.model.predict(X_val)
        mae = mean_absolute_error(y_val, y_pred)
        rmse = np.sqrt(mean_squared_error(y_val, y_pred))
        r2 = r2_score(y_val, y_pred)

        print(f"验证集 MAE: {mae:.2f} 小时")
        print(f"验证集 RMSE: {rmse:.2f} 小时")
        print(f"验证集 R²: {r2:.3f}")

        return self.model

    def get_feature_importance(self):
        """获取特征重要性"""
        importance = pd.DataFrame({
            'feature': self.feature_names,
            'importance': self.model.feature_importances_
        }).sort_values('importance', ascending=False)
        return importance

    def save_model(self, path):
        """保存模型"""
        joblib.dump(self.model, path)

    def load_model(self, path):
        """加载模型"""
        self.model = joblib.load(path)
```

**预测服务（predictor.py）**
```python
class TaskEffortPredictor:
    def __init__(self, model_path, feature_engineer):
        self.model = joblib.load(model_path)
        self.feature_engineer = feature_engineer

    def predict(self, task_data):
        """预测单个任务的工时"""
        # 特征工程
        df = pd.DataFrame([task_data])
        df = self.feature_engineer.create_features(df)

        # 选择模型需要的特征
        X = df[self.model.feature_names_in_]

        # 预测
        predicted_hours = self.model.predict(X)[0]

        # 置信区间（使用分位数回归或历史误差）
        confidence_interval = self._calculate_confidence_interval(predicted_hours)

        return {
            'predicted_hours': round(predicted_hours, 2),
            'confidence_interval': confidence_interval,
            'feature_contributions': self._explain_prediction(X)
        }

    def _calculate_confidence_interval(self, prediction, confidence=0.8):
        """计算置信区间（简化版）"""
        # 基于历史误差的标准差
        std_error = 5.0  # 从训练数据统计得到
        margin = 1.28 * std_error  # 80% 置信区间
        return {
            'lower': max(0, round(prediction - margin, 2)),
            'upper': round(prediction + margin, 2)
        }

    def _explain_prediction(self, X):
        """解释预测结果（使用 SHAP）"""
        import shap
        explainer = shap.TreeExplainer(self.model)
        shap_values = explainer.shap_values(X)

        # 返回 Top 5 影响因素
        feature_impact = pd.DataFrame({
            'feature': X.columns,
            'value': X.iloc[0].values,
            'impact': shap_values[0]
        }).sort_values('impact', key=abs, ascending=False).head(5)

        return feature_impact.to_dict('records')
```

---

## 7. 与 Spring Boot 集成

### 7.1 方案 1：Python 微服务（推荐）

**架构：**
```
Spring Boot Backend (8080)
    ↓ HTTP
Python ML Service (5000)
    ↓
PostgreSQL (5432)
```

**Python Flask API：**
```python
from flask import Flask, request, jsonify
from predictor import TaskEffortPredictor

app = Flask(__name__)
predictor = TaskEffortPredictor('models/xgboost_v1.pkl', feature_engineer)

@app.route('/predict/effort', methods=['POST'])
def predict_effort():
    task_data = request.json
    result = predictor.predict(task_data)
    return jsonify(result)

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
```

**Spring Boot 调用：**
```java
@Service
public class MlPredictionService {

    @Value("${ml.service.url}")
    private String mlServiceUrl;

    private final RestTemplate restTemplate;

    public EffortPredictionResponse predictEffort(Task task) {
        String url = mlServiceUrl + "/predict/effort";

        Map<String, Object> request = Map.of(
            "title", task.getTitle(),
            "type", task.getType().name(),
            "priority", task.getPriority().name(),
            "story_points", task.getStoryPoints(),
            "assignee_id", task.getAssigneeId()
        );

        return restTemplate.postForObject(url, request, EffortPredictionResponse.class);
    }
}
```

### 7.2 方案 2：定期批量预测

**流程：**
1. Python 脚本定期（每天）批量预测所有待办任务
2. 将预测结果写入 `task_effort_predictions` 表
3. Spring Boot 直接查询表获取预测结果

**优势：**
- 响应速度快（直接查数据库）
- 降低 Python 服务压力

**劣势：**
- 预测结果不是实时的

---

## 8. 模型评估指标

### 8.1 回归指标

```python
# MAE（平均绝对误差）- 主要指标
# 含义：预测工时与实际工时的平均偏差
# 目标：< 5 小时（优秀），< 10 小时（良好）

# RMSE（均方根误差）
# 含义：对大误差更敏感
# 目标：< 8 小时

# R²（决定系数）
# 含义：模型解释了多少方差
# 目标：> 0.7（优秀），> 0.5（良好）

# MAPE（平均绝对百分比误差）
# 含义：相对误差百分比
# 目标：< 30%
```

### 8.2 业务指标

```python
# 1. 预测准确率（误差在 ±20% 内的比例）
accuracy_20 = (abs(y_pred - y_true) / y_true <= 0.2).mean()

# 2. 高估/低估比例
overestimate_ratio = (y_pred > y_true).mean()
underestimate_ratio = (y_pred < y_true).mean()

# 3. 不同任务类型的预测精度
for task_type in ['STORY', 'BUG', 'TASK']:
    mask = (df['type'] == task_type)
    mae = mean_absolute_error(y_true[mask], y_pred[mask])
    print(f"{task_type} MAE: {mae:.2f}")
```

---

## 9. 实施路线图

### 阶段 1：数据准备（1 周）
1. 从数据库导出训练数据
2. 数据清洗（处理缺失值、异常值）
3. 探索性数据分析（EDA）
4. 确定特征列表

### 阶段 2：模型开发（2 周）
1. 特征工程实现
2. XGBoost 模型训练
3. 超参数调优
4. 模型评估与验证

### 阶段 3：服务部署（1 周）
1. 开发 Flask API
2. Spring Boot 集成
3. 单元测试与集成测试
4. 部署到测试环境

### 阶段 4：上线与监控（持续）
1. 灰度发布
2. 监控预测准确率
3. 收集用户反馈
4. 模型迭代优化

---

## 10. 风险与挑战

### 10.1 数据质量问题

**风险：**
- 工时记录不准确（用户随意填写）
- 历史数据量不足（< 1000 条）
- 数据分布不均（某些类型任务样本少）

**应对：**
- 数据清洗规则（过滤异常值）
- 冷启动策略（使用规则 + 模型混合）
- 数据增强（SMOTE 等）

### 10.2 模型泛化能力

**风险：**
- 新项目预测不准（缺少历史数据）
- 新团队成员预测不准（缺少个人历史）

**应对：**
- 使用项目级别特征（而非个人级别）
- 迁移学习（从相似项目迁移）

### 10.3 业务接受度

**风险：**
- 用户不信任 AI 预测
- 预测结果与经验差异大

**应对：**
- 提供预测解释（SHAP 值）
- 显示置信区间
- 允许用户手动调整

---

## 11. 总结

**推荐方案：XGBoost + Flask 微服务**

**理由：**
1. 数据量适中（1000-10000 条）
2. 特征以结构化数据为主
3. 需要模型可解释性
4. 团队技能匹配
5. 快速上线（2-4 周）

**后续演进路径：**
- 短期（3 个月）：优化特征工程，提升 MAE 到 < 5 小时
- 中期（6 个月）：引入文本语义特征（BERT embedding）
- 长期（1 年）：数据量增大后，尝试神经网络模型
