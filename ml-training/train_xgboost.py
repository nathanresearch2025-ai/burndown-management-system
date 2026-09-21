"""
XGBoost 任务工时预测训练脚本
可以在 Jupyter Notebook 中通过 %run train_xgboost.py 运行
也可以直接在命令行运行：python train_xgboost.py
"""

import pandas as pd
import numpy as np
import xgboost as xgb
from sklearn.model_selection import train_test_split, cross_val_score
from sklearn.preprocessing import LabelEncoder
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
import joblib
import matplotlib.pyplot as plt
import json
from datetime import datetime

class TaskEffortPredictor:
    """任务工时预测器"""

    def __init__(self):
        self.model = None
        self.label_encoders = {}
        self.feature_names = None
        self.training_history = {}

    def load_data(self, csv_path='training_data.csv'):
        """加载训练数据"""
        print(f"📂 加载数据: {csv_path}")
        df = pd.read_csv(csv_path)
        print(f"✅ 成功加载 {len(df)} 条数据")
        return df

    def prepare_features(self, df, is_training=True):
        """特征工程"""
        print("\n🔧 开始特征工程...")

        df = df.copy()

        # 1. 类别特征编码
        categorical_cols = ['task_type', 'priority']

        for col in categorical_cols:
            if is_training:
                # 训练时：创建并保存编码器
                encoder = LabelEncoder()
                df[f'{col}_encoded'] = encoder.fit_transform(df[col])
                self.label_encoders[col] = encoder
                print(f"  ✓ {col}: {list(encoder.classes_)}")
            else:
                # 预测时：使用已保存的编码器
                encoder = self.label_encoders[col]
                df[f'{col}_encoded'] = encoder.transform(df[col])

        # 2. 选择特征
        feature_cols = [
            'task_type_encoded',
            'priority_encoded',
            'story_points',
            'title_length',
            'description_length',
            'labels_count',
            'created_month',
            'created_quarter',
            'created_day_of_week',
            'days_to_due',
            'assignee_id',
            'project_id'
        ]

        X = df[feature_cols]
        y = df['actual_hours'] if 'actual_hours' in df.columns else None

        if is_training:
            self.feature_names = feature_cols

        print(f"✅ 特征工程完成，共 {len(feature_cols)} 个特征")
        return X, y

    def train(self, X, y, test_size=0.2, random_state=42):
        """训练模型"""
        print("\n🚀 开始训练 XGBoost 模型...")

        # 划分训练集和测试集
        X_train, X_test, y_train, y_test = train_test_split(
            X, y, test_size=test_size, random_state=random_state
        )

        print(f"  训练集: {len(X_train)} 条")
        print(f"  测试集: {len(X_test)} 条")

        # 定义模型参数
        params = {
            'objective': 'reg:squarederror',
            'max_depth': 6,
            'learning_rate': 0.1,
            'n_estimators': 200,
            'subsample': 0.8,
            'colsample_bytree': 0.8,
            'random_state': random_state,
            'n_jobs': -1
        }

        print(f"\n模型参数:")
        for key, value in params.items():
            print(f"  {key}: {value}")

        # 训练模型
        self.model = xgb.XGBRegressor(**params)

        print("\n⏳ 训练中...")
        self.model.fit(
            X_train, y_train,
            eval_set=[(X_test, y_test)],
            verbose=False
        )

        # 预测
        y_train_pred = self.model.predict(X_train)
        y_test_pred = self.model.predict(X_test)

        # 评估
        train_metrics = self._calculate_metrics(y_train, y_train_pred)
        test_metrics = self._calculate_metrics(y_test, y_test_pred)

        # 保存训练历史
        self.training_history = {
            'train_metrics': train_metrics,
            'test_metrics': test_metrics,
            'params': params,
            'feature_names': self.feature_names,
            'training_date': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        }

        # 打印结果
        self._print_metrics(train_metrics, test_metrics)

        return X_test, y_test, y_test_pred

    def _calculate_metrics(self, y_true, y_pred):
        """计算评估指标"""
        mae = mean_absolute_error(y_true, y_pred)
        rmse = np.sqrt(mean_squared_error(y_true, y_pred))
        r2 = r2_score(y_true, y_pred)

        # 计算预测准确率（误差在 ±20% 内）
        accuracy_20 = np.mean(np.abs(y_pred - y_true) / y_true <= 0.2) * 100

        return {
            'mae': mae,
            'rmse': rmse,
            'r2': r2,
            'accuracy_20': accuracy_20
        }

    def _print_metrics(self, train_metrics, test_metrics):
        """打印评估指标"""
        print("\n" + "="*60)
        print("📊 模型评估结果")
        print("="*60)

        print("\n训练集:")
        print(f"  MAE (平均绝对误差):     {train_metrics['mae']:.2f} 小时")
        print(f"  RMSE (均方根误差):       {train_metrics['rmse']:.2f} 小时")
        print(f"  R² (决定系数):           {train_metrics['r2']:.3f}")
        print(f"  预测准确率 (±20%):       {train_metrics['accuracy_20']:.1f}%")

        print("\n测试集:")
        print(f"  MAE (平均绝对误差):     {test_metrics['mae']:.2f} 小时")
        print(f"  RMSE (均方根误差):       {test_metrics['rmse']:.2f} 小时")
        print(f"  R² (决定系数):           {test_metrics['r2']:.3f}")
        print(f"  预测准确率 (±20%):       {test_metrics['accuracy_20']:.1f}%")

        print("\n" + "="*60)

        # 评估结论
        if test_metrics['mae'] < 5:
            print("✅ 优秀！MAE < 5 小时")
        elif test_metrics['mae'] < 10:
            print("✅ 良好！MAE < 10 小时")
        else:
            print("⚠️  需要优化，MAE 较高")

    def get_feature_importance(self, top_n=10):
        """获取特征重要性"""
        if self.model is None:
            print("❌ 模型尚未训练")
            return None

        importance = pd.DataFrame({
            'feature': self.feature_names,
            'importance': self.model.feature_importances_
        }).sort_values('importance', ascending=False)

        print(f"\n📈 Top {top_n} 重要特征:")
        print(importance.head(top_n).to_string(index=False))

        return importance

    def plot_results(self, y_test, y_pred, save_path='results.png'):
        """可视化预测结果"""
        print(f"\n📊 生成可视化图表...")

        fig, axes = plt.subplots(1, 2, figsize=(14, 5))

        # 图1: 预测 vs 实际
        axes[0].scatter(y_test, y_pred, alpha=0.5, s=20)
        axes[0].plot([y_test.min(), y_test.max()],
                     [y_test.min(), y_test.max()],
                     'r--', lw=2, label='理想预测线')
        axes[0].set_xlabel('实际工时（小时）', fontsize=12)
        axes[0].set_ylabel('预测工时（小时）', fontsize=12)
        axes[0].set_title('预测 vs 实际工时', fontsize=14, fontweight='bold')
        axes[0].legend()
        axes[0].grid(True, alpha=0.3)

        # 图2: 误差分布
        errors = y_pred - y_test
        axes[1].hist(errors, bins=30, edgecolor='black', alpha=0.7)
        axes[1].axvline(x=0, color='r', linestyle='--', lw=2, label='零误差线')
        axes[1].set_xlabel('预测误差（小时）', fontsize=12)
        axes[1].set_ylabel('频数', fontsize=12)
        axes[1].set_title('预测误差分布', fontsize=14, fontweight='bold')
        axes[1].legend()
        axes[1].grid(True, alpha=0.3)

        plt.tight_layout()
        plt.savefig(save_path, dpi=150, bbox_inches='tight')
        print(f"✅ 图表已保存: {save_path}")

        return fig

    def save_model(self, model_path='xgboost_model.pkl',
                   encoders_path='label_encoders.pkl',
                   history_path='training_history.json'):
        """保存模型和编码器"""
        print(f"\n💾 保存模型...")

        # 保存模型
        joblib.dump(self.model, model_path)
        print(f"  ✓ 模型: {model_path}")

        # 保存编码器
        joblib.dump(self.label_encoders, encoders_path)
        print(f"  ✓ 编码器: {encoders_path}")

        # 保存训练历史
        with open(history_path, 'w', encoding='utf-8') as f:
            json.dump(self.training_history, f, indent=2, ensure_ascii=False)
        print(f"  ✓ 训练历史: {history_path}")

        print("✅ 模型保存完成")

    def load_model(self, model_path='xgboost_model.pkl',
                   encoders_path='label_encoders.pkl'):
        """加载已保存的模型"""
        print(f"📂 加载模型...")

        self.model = joblib.load(model_path)
        self.label_encoders = joblib.load(encoders_path)

        print("✅ 模型加载完成")

    def predict(self, task_data):
        """预测单个任务的工时"""
        if self.model is None:
            raise ValueError("模型尚未训练或加载")

        # 转换为 DataFrame
        if isinstance(task_data, dict):
            df = pd.DataFrame([task_data])
        else:
            df = task_data

        # 特征工程
        X, _ = self.prepare_features(df, is_training=False)

        # 预测
        predicted_hours = self.model.predict(X)

        return predicted_hours[0] if len(predicted_hours) == 1 else predicted_hours


def main():
    """主函数"""
    print("="*60)
    print("🤖 XGBoost 任务工时预测训练")
    print("="*60)

    # 1. 初始化预测器
    predictor = TaskEffortPredictor()

    # 2. 加载数据
    df = predictor.load_data('training_data.csv')

    # 3. 特征工程
    X, y = predictor.prepare_features(df, is_training=True)

    # 4. 训练模型
    X_test, y_test, y_pred = predictor.train(X, y)

    # 5. 特征重要性
    predictor.get_feature_importance(top_n=10)

    # 6. 可视化结果
    predictor.plot_results(y_test, y_pred, save_path='training_results.png')

    # 7. 保存模型
    predictor.save_model(
        model_path='xgboost_model.pkl',
        encoders_path='label_encoders.pkl',
        history_path='training_history.json'
    )

    # 8. 测试预测
    print("\n" + "="*60)
    print("🧪 测试预测功能")
    print("="*60)

    test_task = {
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

    predicted = predictor.predict(test_task)
    print(f"\n测试任务:")
    print(f"  类型: {test_task['task_type']}")
    print(f"  优先级: {test_task['priority']}")
    print(f"  故事点: {test_task['story_points']}")
    print(f"\n预测工时: {predicted:.1f} 小时")

    print("\n" + "="*60)
    print("✅ 训练完成！")
    print("="*60)
    print("\n生成的文件:")
    print("  1. xgboost_model.pkl - 训练好的模型")
    print("  2. label_encoders.pkl - 特征编码器")
    print("  3. training_history.json - 训练历史")
    print("  4. training_results.png - 可视化结果")


if __name__ == '__main__':
    main()
