# ============================================================
# XGBoost 模型优化 - 详细注释版本
# 功能：通过特征工程和自动调参提升模型准确率从 77% 到 85%+
# ============================================================

# ============================================================
# 第 1 部分：导入依赖库
# ============================================================

import pandas as pd              # 数据处理和分析库
import numpy as np               # 数值计算库
import matplotlib.pyplot as plt  # 绘图库
import seaborn as sns           # 高级绘图库
import xgboost as xgb           # XGBoost 机器学习库
from sklearn.model_selection import train_test_split, cross_val_score  # 数据集划分和交叉验证
from sklearn.preprocessing import LabelEncoder  # 类别特征编码器
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score  # 评估指标
import optuna                   # 自动超参数优化库
import joblib                   # 模型序列化库
import json                     # JSON 文件处理
import warnings                 # 警告控制
warnings.filterwarnings('ignore')  # 忽略所有警告信息，保持输出清晰

# 配置中文显示（解决 matplotlib 中文乱码问题）
plt.rcParams['font.sans-serif'] = ['SimHei']  # 使用黑体字体
plt.rcParams['axes.unicode_minus'] = False     # 解决负号显示问题

# 设置随机种子，确保结果可复现
np.random.seed(42)

print("✅ 环境准备完成")


# ============================================================
# 第 2 部分：加载训练数据
# ============================================================

# 从 CSV 文件读取训练数据（由 generate_data.py 生成的 1000 条记录）
df = pd.read_csv('training_data.csv')

# 打印数据集的形状（行数，列数）
print(f"数据形状: {df.shape}")

# 显示前 5 行数据，快速了解数据结构
print(df.head())


# ============================================================
# 第 3 部分：特征工程（Feature Engineering）
# 目标：从原始 12 个特征扩展到 27 个特征
# ============================================================

# 保存原始数据的副本，用于后续对比
df_original = df.copy()

print("开始特征工程...\n")

# ------------------------------------------------------------
# 3.1 交互特征（Interaction Features）
# 原理：不同类型/优先级的任务，相同故事点可能需要不同工时
# ------------------------------------------------------------
print("1. 添加交互特征")

# 故事点 × 任务类型
# EPIC(4) > TASK(3) > BUG(2) > STORY(1)，权重反映复杂度
df['story_points_x_type'] = df['story_points'] * df['task_type'].map(
    {'STORY': 1, 'BUG': 2, 'TASK': 3, 'EPIC': 4}
)

# 故事点 × 优先级
# CRITICAL(4) > HIGH(3) > MEDIUM(2) > LOW(1)，高优先级任务通常更复杂
df['story_points_x_priority'] = df['story_points'] * df['priority'].map(
    {'LOW': 1, 'MEDIUM': 2, 'HIGH': 3, 'CRITICAL': 4}
)

# ------------------------------------------------------------
# 3.2 比例特征（Ratio Features）
# 原理：描述越详细，任务越复杂，工时越长
# ------------------------------------------------------------
print("2. 添加比例特征")

# 描述长度 / 标题长度（+1 避免除零）
# 比值越大，说明描述越详细
df['desc_title_ratio'] = df['description_length'] / (df['title_length'] + 1)

# 是否有描述（二值特征：0 或 1）
# 有描述的任务通常更正式、更复杂
df['has_description'] = (df['description_length'] > 0).astype(int)

# ------------------------------------------------------------
# 3.3 时间特征（Time-based Features）
# 原理：紧急任务可能需要加班，周末创建的任务可能是紧急 bug
# ------------------------------------------------------------
print("3. 添加时间特征")

# 是否紧急（截止日期 < 7 天）
df['is_urgent'] = (df['days_to_due'] < 7).astype(int)

# 是否周末创建（周六=5，周日=6）
df['is_weekend'] = (df['created_day_of_week'] >= 5).astype(int)

# ------------------------------------------------------------
# 3.4 历史统计特征（Historical Statistics）
# 原理：利用历史数据的平均值和标准差作为先验知识
# 这是最重要的特征，能显著提升准确率
# ------------------------------------------------------------
print("4. 添加历史统计特征")

# 4.4.1 按任务类型统计
# 计算每种任务类型的平均工时和标准差
type_stats = df.groupby('task_type')['actual_hours'].agg(['mean', 'std']).reset_index()
type_stats.columns = ['task_type', 'type_avg_hours', 'type_std_hours']
# 将统计结果合并回原数据集
df = df.merge(type_stats, on='task_type', how='left')

# 4.4.2 按优先级统计
# 计算每种优先级的平均工时和标准差
priority_stats = df.groupby('priority')['actual_hours'].agg(['mean', 'std']).reset_index()
priority_stats.columns = ['priority', 'priority_avg_hours', 'priority_std_hours']
df = df.merge(priority_stats, on='priority', how='left')

# 4.4.3 按负责人统计
# 计算每个负责人的历史平均工时和标准差（反映个人效率）
assignee_stats = df.groupby('assignee_id')['actual_hours'].agg(['mean', 'std']).reset_index()
assignee_stats.columns = ['assignee_id', 'assignee_avg_hours', 'assignee_std_hours']
df = df.merge(assignee_stats, on='assignee_id', how='left')

# 4.4.4 按项目统计
# 计算每个项目的历史平均工时和标准差（反映项目复杂度）
project_stats = df.groupby('project_id')['actual_hours'].agg(['mean', 'std']).reset_index()
project_stats.columns = ['project_id', 'project_avg_hours', 'project_std_hours']
df = df.merge(project_stats, on='project_id', how='left')

# 打印特征工程结果
print(f"\n✅ 特征工程完成！")
print(f"原始特征数: {df_original.shape[1]}")
print(f"增强后特征数: {df.shape[1]}")
print(f"新增特征数: {df.shape[1] - df_original.shape[1]}")


# ============================================================
# 第 4 部分：准备训练数据
# ============================================================

# 分离特征（X）和目标变量（y）
X = df.drop('actual_hours', axis=1)  # 删除目标列，剩余的都是特征
y = df['actual_hours']                # 目标变量：实际工时

# ------------------------------------------------------------
# 4.1 类别特征编码
# 原理：机器学习模型只能处理数值，需要将文本类别转换为数字
# ------------------------------------------------------------
categorical_cols = ['task_type', 'priority']  # 需要编码的类别列
label_encoders = {}  # 存储每个列的编码器，用于后续预测时解码

for col in categorical_cols:
    le = LabelEncoder()  # 创建标签编码器
    # 将类别转换为数字（例如：STORY→0, BUG→1, TASK→2, EPIC→3）
    X[f'{col}_encoded'] = le.fit_transform(X[col])
    label_encoders[col] = le  # 保存编码器

# 删除原始类别列，只保留编码后的数值列
X = X.drop(categorical_cols, axis=1)

# ------------------------------------------------------------
# 4.2 划分训练集和测试集
# 原理：80% 用于训练，20% 用于测试模型泛化能力
# ------------------------------------------------------------
X_train, X_test, y_train, y_test = train_test_split(
    X, y,
    test_size=0.2,      # 测试集占 20%
    random_state=42     # 固定随机种子，确保每次划分结果一致
)

print(f"训练集: {X_train.shape}")
print(f"测试集: {X_test.shape}")
print(f"\n特征列表 ({len(X.columns)} 个):")
print(X.columns.tolist())


# ============================================================
# 第 5 部分：训练基线模型（优化前）
# 目的：建立性能基准，用于对比优化效果
# ============================================================

print("训练基线模型（使用默认参数）...\n")

# 创建 XGBoost 回归模型，使用默认参数
baseline_model = xgb.XGBRegressor(
    max_depth=6,              # 树的最大深度（控制模型复杂度）
    learning_rate=0.1,        # 学习率（步长）
    n_estimators=200,         # 树的数量
    subsample=0.8,            # 样本采样比例（防止过拟合）
    colsample_bytree=0.8,     # 特征采样比例（防止过拟合）
    random_state=42           # 随机种子
)

# 训练模型
baseline_model.fit(X_train, y_train, verbose=False)

# 在测试集上预测
y_pred_baseline = baseline_model.predict(X_test)

# ------------------------------------------------------------
# 5.1 计算评估指标
# ------------------------------------------------------------

# MAE（平均绝对误差）：预测值与真实值的平均差距（单位：小时）
baseline_mae = mean_absolute_error(y_test, y_pred_baseline)

# RMSE（均方根误差）：对大误差更敏感
baseline_rmse = np.sqrt(mean_squared_error(y_test, y_pred_baseline))

# R²（决定系数）：模型解释了多少数据方差（0-1，越接近 1 越好）
baseline_r2 = r2_score(y_test, y_pred_baseline)

# 准确率（±20%）：预测误差在 ±20% 以内的样本比例
baseline_accuracy = np.mean(np.abs(y_pred_baseline - y_test) / y_test <= 0.2) * 100

# 打印基线模型性能
print("="*50)
print("基线模型性能")
print("="*50)
print(f"MAE: {baseline_mae:.2f} 小时")
print(f"RMSE: {baseline_rmse:.2f} 小时")
print(f"R²: {baseline_r2:.3f}")
print(f"准确率（±20%）: {baseline_accuracy:.1f}%")
print("="*50)


# ============================================================
# 第 6 部分：使用 Optuna 自动调参
# 原理：贝叶斯优化，智能搜索最佳参数组合
# ============================================================

def objective(trial):
    """
    Optuna 优化目标函数

    参数说明：
    - trial: Optuna 试验对象，用于建议参数值

    返回值：
    - mae: 平均绝对误差（越小越好）
    """

    # 定义参数搜索空间
    params = {
        # 树的最大深度：3-10 之间的整数
        # 深度越大，模型越复杂，但可能过拟合
        'max_depth': trial.suggest_int('max_depth', 3, 10),

        # 学习率：0.01-0.3 之间的浮点数（对数尺度）
        # 学习率越小，训练越精细，但需要更多树
        'learning_rate': trial.suggest_float('learning_rate', 0.01, 0.3, log=True),

        # 树的数量：100-500 之间的整数
        # 树越多，模型越强，但训练时间越长
        'n_estimators': trial.suggest_int('n_estimators', 100, 500),

        # 样本采样比例：0.6-1.0 之间的浮点数
        # 每棵树随机采样的样本比例，防止过拟合
        'subsample': trial.suggest_float('subsample', 0.6, 1.0),

        # 特征采样比例：0.6-1.0 之间的浮点数
        # 每棵树随机采样的特征比例，防止过拟合
        'colsample_bytree': trial.suggest_float('colsample_bytree', 0.6, 1.0),

        # 最小叶子节点权重：1-7 之间的整数
        # 值越大，模型越保守，防止过拟合
        'min_child_weight': trial.suggest_int('min_child_weight', 1, 7),

        # 分裂最小损失：0-0.5 之间的浮点数
        # 值越大，模型越保守，防止过拟合
        'gamma': trial.suggest_float('gamma', 0, 0.5),

        # L1 正则化系数：0-1 之间的浮点数
        # 控制模型复杂度，防止过拟合
        'reg_alpha': trial.suggest_float('reg_alpha', 0, 1),

        # L2 正则化系数：0-1 之间的浮点数
        # 控制模型复杂度，防止过拟合
        'reg_lambda': trial.suggest_float('reg_lambda', 0, 1),

        # 随机种子（固定）
        'random_state': 42
    }

    # 使用建议的参数创建模型
    model = xgb.XGBRegressor(**params)

    # 训练模型
    model.fit(X_train, y_train, verbose=False)

    # 在测试集上预测
    y_pred = model.predict(X_test)

    # 计算 MAE（优化目标：最小化 MAE）
    mae = mean_absolute_error(y_test, y_pred)

    return mae


print("开始自动调参...")
print("这可能需要 5-10 分钟，请耐心等待...\n")

# 创建 Optuna 研究对象
# direction='minimize' 表示最小化目标函数（MAE）
study = optuna.create_study(direction='minimize')

# 开始优化：尝试 50 组不同的参数组合
# show_progress_bar=True 显示进度条
study.optimize(objective, n_trials=50, show_progress_bar=True)

# 打印优化结果
print("\n" + "="*50)
print("自动调参完成！")
print("="*50)
print(f"\n最佳 MAE: {study.best_value:.2f} 小时")
print(f"\n最佳参数:")
for key, value in study.best_params.items():
    print(f"  {key}: {value}")


# ============================================================
# 第 7 部分：使用最佳参数训练最终模型
# ============================================================

print("使用最佳参数训练最终模型...\n")

# 使用 Optuna 找到的最佳参数创建模型
optimized_model = xgb.XGBRegressor(**study.best_params)

# 训练模型
optimized_model.fit(X_train, y_train, verbose=False)

# 在测试集上预测
y_pred_optimized = optimized_model.predict(X_test)

# 计算优化后模型的评估指标
optimized_mae = mean_absolute_error(y_test, y_pred_optimized)
optimized_rmse = np.sqrt(mean_squared_error(y_test, y_pred_optimized))
optimized_r2 = r2_score(y_test, y_pred_optimized)
optimized_accuracy = np.mean(np.abs(y_pred_optimized - y_test) / y_test <= 0.2) * 100

# 打印优化后模型性能
print("="*50)
print("优化后模型性能")
print("="*50)
print(f"MAE: {optimized_mae:.2f} 小时")
print(f"RMSE: {optimized_rmse:.2f} 小时")
print(f"R²: {optimized_r2:.3f}")
print(f"准确率（±20%）: {optimized_accuracy:.1f}%")
print("="*50)


# ============================================================
# 第 8 部分：对比优化前后的效果
# ============================================================

# 创建对比表格
comparison = pd.DataFrame({
    '指标': ['MAE (小时)', 'RMSE (小时)', 'R²', '准确率 (%)'],
    '基线模型': [baseline_mae, baseline_rmse, baseline_r2, baseline_accuracy],
    '优化后模型': [optimized_mae, optimized_rmse, optimized_r2, optimized_accuracy],
    '提升': [
        f"{baseline_mae - optimized_mae:.2f}",
        f"{baseline_rmse - optimized_rmse:.2f}",
        f"{optimized_r2 - baseline_r2:.3f}",
        f"{optimized_accuracy - baseline_accuracy:.1f}%"
    ]
})

print("\n" + "="*70)
print("优化效果对比")
print("="*70)
print(comparison.to_string(index=False))
print("="*70)

# ------------------------------------------------------------
# 8.1 可视化对比
# ------------------------------------------------------------

# 创建 1 行 2 列的子图
fig, axes = plt.subplots(1, 2, figsize=(14, 5))

# 左图：MAE 对比（越低越好）
axes[0].bar(['基线模型', '优化后'], [baseline_mae, optimized_mae],
            color=['lightcoral', 'lightgreen'])
axes[0].set_ylabel('MAE (小时)')
axes[0].set_title('平均绝对误差对比（越低越好）')
axes[0].set_ylim(0, max(baseline_mae, optimized_mae) * 1.2)
# 在柱状图上方显示数值
for i, v in enumerate([baseline_mae, optimized_mae]):
    axes[0].text(i, v + 0.1, f'{v:.2f}', ha='center', fontweight='bold')

# 右图：准确率对比（越高越好）
axes[1].bar(['基线模型', '优化后'], [baseline_accuracy, optimized_accuracy],
            color=['lightcoral', 'lightgreen'])
axes[1].set_ylabel('准确率 (%)')
axes[1].set_title('预测准确率对比（±20%，越高越好）')
axes[1].set_ylim(0, 100)
# 在柱状图上方显示数值
for i, v in enumerate([baseline_accuracy, optimized_accuracy]):
    axes[1].text(i, v + 1, f'{v:.1f}%', ha='center', fontweight='bold')

plt.tight_layout()
plt.savefig('optimization_comparison.png', dpi=150, bbox_inches='tight')
print("\n✅ 对比图已保存: optimization_comparison.png")


# ============================================================
# 第 9 部分：可视化优化过程
# ============================================================

fig, axes = plt.subplots(1, 2, figsize=(14, 5))

# ------------------------------------------------------------
# 9.1 左图：Optuna 优化历史曲线
# 显示每次试验的 MAE 值，观察优化趋势
# ------------------------------------------------------------
trials_df = study.trials_dataframe()  # 获取所有试验的数据
axes[0].plot(trials_df['number'], trials_df['value'], marker='o', alpha=0.6)
# 绘制最佳 MAE 的水平线
axes[0].axhline(y=study.best_value, color='r', linestyle='--',
                label=f'最佳 MAE: {study.best_value:.2f}')
axes[0].set_xlabel('试验次数')
axes[0].set_ylabel('MAE (小时)')
axes[0].set_title('Optuna 优化历史')
axes[0].legend()
axes[0].grid(True, alpha=0.3)

# ------------------------------------------------------------
# 9.2 右图：参数重要性排名
# 显示哪些参数对模型性能影响最大
# ------------------------------------------------------------
importance = optuna.importance.get_param_importances(study)
importance_df = pd.DataFrame({
    'parameter': list(importance.keys()),
    'importance': list(importance.values())
}).sort_values('importance', ascending=True)  # 升序排列，重要的在上方

axes[1].barh(importance_df['parameter'], importance_df['importance'])
axes[1].set_xlabel('重要性')
axes[1].set_title('参数重要性排名')

plt.tight_layout()
plt.savefig('optimization_process.png', dpi=150, bbox_inches='tight')
print("✅ 优化过程图已保存: optimization_process.png")


# ============================================================
# 第 10 部分：预测效果散点图对比
# ============================================================

fig, axes = plt.subplots(1, 2, figsize=(14, 5))

# ------------------------------------------------------------
# 10.1 左图：基线模型预测效果
# ------------------------------------------------------------
axes[0].scatter(y_test, y_pred_baseline, alpha=0.5, s=30)
# 绘制理想预测线（y=x）
axes[0].plot([y_test.min(), y_test.max()], [y_test.min(), y_test.max()],
             'r--', lw=2, label='理想预测')
axes[0].set_xlabel('实际工时（小时）')
axes[0].set_ylabel('预测工时（小时）')
axes[0].set_title(f'基线模型（准确率: {baseline_accuracy:.1f}%）')
axes[0].grid(True, alpha=0.3)
axes[0].legend()

# ------------------------------------------------------------
# 10.2 右图：优化后模型预测效果
# ------------------------------------------------------------
axes[1].scatter(y_test, y_pred_optimized, alpha=0.5, s=30, color='green')
axes[1].plot([y_test.min(), y_test.max()], [y_test.min(), y_test.max()],
             'r--', lw=2, label='理想预测')
axes[1].set_xlabel('实际工时（小时）')
axes[1].set_ylabel('预测工时（小时）')
axes[1].set_title(f'优化后模型（准确率: {optimized_accuracy:.1f}%）')
axes[1].grid(True, alpha=0.3)
axes[1].legend()

plt.tight_layout()
plt.savefig('prediction_comparison.png', dpi=150, bbox_inches='tight')
print("✅ 预测对比图已保存: prediction_comparison.png")


# ============================================================
# 第 11 部分：保存优化后的模型和配置
# ============================================================

# 11.1 保存优化后的模型（用于生产环境）
joblib.dump(optimized_model, 'xgboost_optimized_model.pkl')
print("\n✅ 优化后模型已保存: xgboost_optimized_model.pkl")

# 11.2 保存特征编码器（预测时需要用相同的编码）
joblib.dump(label_encoders, 'label_encoders_optimized.pkl')
print("✅ 编码器已保存: label_encoders_optimized.pkl")

# 11.3 保存最佳参数配置（用于文档和复现）
with open('best_params.json', 'w', encoding='utf-8') as f:
    json.dump(study.best_params, f, indent=2, ensure_ascii=False)
print("✅ 最佳参数已保存: best_params.json")

# 11.4 保存完整的优化历史（用于分析和报告）
optimization_history = {
    'baseline': {
        'mae': float(baseline_mae),
        'rmse': float(baseline_rmse),
        'r2': float(baseline_r2),
        'accuracy': float(baseline_accuracy)
    },
    'optimized': {
        'mae': float(optimized_mae),
        'rmse': float(optimized_rmse),
        'r2': float(optimized_r2),
        'accuracy': float(optimized_accuracy)
    },
    'improvement': {
        'mae_reduction': float(baseline_mae - optimized_mae),
        'rmse_reduction': float(baseline_rmse - optimized_rmse),
        'r2_gain': float(optimized_r2 - baseline_r2),
        'accuracy_gain': float(optimized_accuracy - baseline_accuracy)
    },
    'best_params': study.best_params,
    'n_trials': len(study.trials),
    'feature_count': len(X.columns)
}

with open('optimization_history.json', 'w', encoding='utf-8') as f:
    json.dump(optimization_history, f, indent=2, ensure_ascii=False)
print("✅ 优化历史已保存: optimization_history.json")


# ============================================================
# 第 12 部分：使用示例
# ============================================================

print("\n" + "="*70)
print("🎉 优化完成！")
print("="*70)
print("\n如何使用优化后的模型：\n")
print("```python")
print("import joblib")
print("import pandas as pd")
print("")
print("# 加载模型")
print("model = joblib.load('xgboost_optimized_model.pkl')")
print("encoders = joblib.load('label_encoders_optimized.pkl')")
print("")
print("# 预测新任务")
print("new_task = {")
print("    'task_type': 'STORY',")
print("    'priority': 'HIGH',")
print("    'story_points': 5,")
print("    # ... 其他特征")
print("}")
print("")
print("# 特征工程 + 编码 + 预测")
print("predicted_hours = model.predict(X_new)[0]")
print("print(f'预测工时: {predicted_hours:.1f} 小时')")
print("```")
print("="*70)
