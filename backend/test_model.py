#!/usr/bin/env python3
"""
测试 Sprint 预测模型是否可以正常加载和推理
"""

import sys
import json
import joblib
import numpy as np
import warnings
warnings.filterwarnings('ignore')

def test_model():
    try:
        print("=== Sprint 预测模型测试 ===")

        # 测试模型文件路径
        model_path = "D:/java/claude/projects/2/backend/src/main/resources/models/random_forest_model.pkl"
        feature_path = "D:/java/claude/projects/2/backend/src/main/resources/models/feature_columns.json"

        print(f"1. 加载模型: {model_path}")
        model = joblib.load(model_path)
        print(f"   模型类型: {type(model).__name__}")
        print(f"   特征数量: {model.n_features_in_}")

        print(f"\n2. 加载特征配置: {feature_path}")
        with open(feature_path, 'r', encoding='utf-8') as f:
            config = json.load(f)
            feature_columns = config['feature_columns']

        print(f"   特征列数: {len(feature_columns)}")
        print(f"   特征列: {feature_columns[:5]}...")  # 显示前5个

        print(f"\n3. 测试推理")
        # 构造测试特征向量（模拟一个中等风险的 Sprint）
        test_features = {
            "sprint_days": 14.0,
            "days_elapsed": 7.0,
            "committed_sp": 60.0,
            "remaining_sp": 25.0,
            "completed_sp": 35.0,
            "velocity_current": 5.0,
            "velocity_avg_5": 4.8,
            "velocity_std_5": 0.8,
            "blocked_stories": 1.0,
            "attendance_rate": 0.9,
            "ratio_feature": 0.6,
            "ratio_bug": 0.2,
            "ratio_tech_debt": 0.2,
            "days_remaining": 7.0,
            "elapsed_ratio": 0.5,
            "remaining_ratio": 0.417,
            "velocity_gap": 0.2,
            "projected_sp": 70.0,
            "projected_completion_ratio": 1.17
        }

        # 按特征列顺序构建向量
        feature_vector = [test_features.get(col, 0.0) for col in feature_columns]
        X_test = np.array([feature_vector])

        print(f"   特征向量长度: {len(feature_vector)}")
        print(f"   特征向量示例: {feature_vector[:5]}...")

        # 预测
        probability = model.predict_proba(X_test)[0][1]
        prediction = model.predict(X_test)[0]

        # 映射风险等级
        if probability >= 0.8:
            risk_level = "GREEN"
        elif probability >= 0.5:
            risk_level = "YELLOW"
        else:
            risk_level = "RED"

        print(f"\n4. 预测结果")
        print(f"   完成概率: {probability:.4f} ({probability*100:.2f}%)")
        print(f"   预测标签: {prediction}")
        print(f"   风险等级: {risk_level}")

        print(f"\n✅ 模型测试成功！")
        return True

    except Exception as e:
        print(f"\n❌ 模型测试失败: {str(e)}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    success = test_model()
    sys.exit(0 if success else 1)