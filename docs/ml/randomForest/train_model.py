"""
随机森林 Sprint 完成预测 - 模型训练脚本
依赖：pip install scikit-learn pandas numpy matplotlib joblib
运行：python train_model.py
输出：random_forest_model.pkl  feature_columns.json  training_report.txt
"""

import json
import os
import warnings

import joblib
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import (
    ConfusionMatrixDisplay,
    accuracy_score,
    classification_report,
    roc_auc_score,
    roc_curve,
)
from sklearn.model_selection import StratifiedKFold, cross_val_score, train_test_split
from sklearn.preprocessing import StandardScaler

warnings.filterwarnings("ignore")

# ─── 配置 ────────────────────────────────────────────────────────────────────
DATA_PATH = "sprint_training_data.csv"
MODEL_PATH = "random_forest_model.pkl"
FEATURE_COLUMNS_PATH = "feature_columns.json"
REPORT_PATH = "training_report.txt"
FIGURES_DIR = "figures"

FEATURE_COLS = [
    "sprint_days",
    "days_elapsed",
    "committed_sp",
    "remaining_sp",
    "completed_sp",
    "velocity_current",
    "velocity_avg_5",
    "velocity_std_5",
    "blocked_stories",
    "attendance_rate",
    "ratio_feature",
    "ratio_bug",
    "ratio_tech_debt",
]
TARGET_COL = "label_completed"

RF_PARAMS = {
    "n_estimators": 200,
    "max_depth": 10,
    "min_samples_split": 5,
    "min_samples_leaf": 2,
    "max_features": "sqrt",
    "class_weight": "balanced",
    "random_state": 42,
    "n_jobs": -1,
}
# ─────────────────────────────────────────────────────────────────────────────


def load_data(path: str) -> pd.DataFrame:
    print(f"[1/6] 加载数据：{path}")
    df = pd.read_csv(path)
    print(f"      行数={len(df)}，列数={len(df.columns)}")
    missing = df[FEATURE_COLS + [TARGET_COL]].isnull().sum()
    if missing.any():
        print(f"      缺失值检测：\n{missing[missing > 0]}")
        df[FEATURE_COLS] = df[FEATURE_COLS].fillna(df[FEATURE_COLS].median())
    return df


def engineer_features(df: pd.DataFrame) -> pd.DataFrame:
    """添加衍生特征"""
    df = df.copy()
    df["days_remaining"] = df["sprint_days"] - df["days_elapsed"]
    df["elapsed_ratio"] = df["days_elapsed"] / df["sprint_days"].clip(lower=1)
    df["remaining_ratio"] = df["remaining_sp"] / df["committed_sp"].clip(lower=1)
    df["velocity_gap"] = df["velocity_current"] - df["velocity_avg_5"]
    df["projected_sp"] = df["completed_sp"] + df["velocity_current"] * df["days_remaining"].clip(lower=0)
    df["projected_completion_ratio"] = df["projected_sp"] / df["committed_sp"].clip(lower=1)
    return df


def split_data(df: pd.DataFrame, feature_cols: list):
    X = df[feature_cols].values
    y = df[TARGET_COL].values
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, stratify=y, random_state=42
    )
    print(f"[2/6] 数据划分：训练={len(X_train)}，测试={len(X_test)}")
    return X_train, X_test, y_train, y_test


def train_model(X_train, y_train) -> RandomForestClassifier:
    print("[3/6] 训练 RandomForestClassifier ...")
    model = RandomForestClassifier(**RF_PARAMS)
    model.fit(X_train, y_train)
    print("      训练完成")
    return model


def cross_validate(model, X_train, y_train) -> None:
    print("[4/6] 5-折交叉验证 ...")
    cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)
    cv_scores = cross_val_score(model, X_train, y_train, cv=cv, scoring="roc_auc")
    print(f"      CV AUC：{cv_scores.round(4)}  均值={cv_scores.mean():.4f}  标准差={cv_scores.std():.4f}")


def evaluate(model, X_test, y_test, feature_cols: list) -> dict:
    print("[5/6] 评估测试集 ...")
    y_pred = model.predict(X_test)
    y_prob = model.predict_proba(X_test)[:, 1]

    acc = accuracy_score(y_test, y_pred)
    auc = roc_auc_score(y_test, y_prob)
    report = classification_report(y_test, y_pred, target_names=["未完成", "完成"])

    print(f"      Accuracy : {acc:.4f}")
    print(f"      ROC-AUC  : {auc:.4f}")
    print(f"\n{report}")

    # 特征重要性
    importances = pd.Series(model.feature_importances_, index=feature_cols)
    top_features = importances.sort_values(ascending=False)
    print("      Top 特征重要性：")
    print(top_features.to_string())

    return {
        "accuracy": acc,
        "roc_auc": auc,
        "report": report,
        "feature_importances": top_features,
        "y_pred": y_pred,
        "y_prob": y_prob,
    }


def save_figures(model, X_test, y_test, eval_result: dict, feature_cols: list) -> None:
    os.makedirs(FIGURES_DIR, exist_ok=True)

    # ROC 曲线
    fpr, tpr, _ = roc_curve(y_test, eval_result["y_prob"])
    fig, axes = plt.subplots(1, 3, figsize=(18, 5))

    axes[0].plot(fpr, tpr, color="steelblue", lw=2, label=f"AUC = {eval_result['roc_auc']:.4f}")
    axes[0].plot([0, 1], [0, 1], "k--", lw=1)
    axes[0].set_xlabel("False Positive Rate")
    axes[0].set_ylabel("True Positive Rate")
    axes[0].set_title("ROC Curve")
    axes[0].legend(loc="lower right")

    # 特征重要性
    fi = eval_result["feature_importances"].head(10)
    fi.sort_values().plot(kind="barh", ax=axes[1], color="steelblue")
    axes[1].set_title("Top 10 Feature Importances")
    axes[1].set_xlabel("Importance")

    # 混淆矩阵
    ConfusionMatrixDisplay.from_predictions(
        y_test, eval_result["y_pred"],
        display_labels=["未完成", "完成"],
        ax=axes[2],
        colorbar=False,
    )
    axes[2].set_title("Confusion Matrix")

    plt.tight_layout()
    fig_path = os.path.join(FIGURES_DIR, "training_summary.png")
    plt.savefig(fig_path, dpi=120)
    plt.close()
    print(f"      图表已保存：{fig_path}")


def save_artifacts(model, feature_cols: list, eval_result: dict) -> None:
    print("[6/6] 保存模型产物 ...")

    joblib.dump(model, MODEL_PATH)
    print(f"      模型：{MODEL_PATH}")

    with open(FEATURE_COLUMNS_PATH, "w", encoding="utf-8") as f:
        json.dump({"feature_columns": feature_cols}, f, ensure_ascii=False, indent=2)
    print(f"      特征列：{FEATURE_COLUMNS_PATH}")

    with open(REPORT_PATH, "w", encoding="utf-8") as f:
        f.write("=" * 60 + "\n")
        f.write("  随机森林 Sprint 完成预测 - 训练报告\n")
        f.write("=" * 60 + "\n\n")
        f.write(f"特征列数：{len(feature_cols)}\n")
        f.write(f"特征列：{feature_cols}\n\n")
        f.write(f"Accuracy : {eval_result['accuracy']:.4f}\n")
        f.write(f"ROC-AUC  : {eval_result['roc_auc']:.4f}\n\n")
        f.write("分类报告：\n")
        f.write(eval_result["report"])
        f.write("\n特征重要性：\n")
        f.write(eval_result["feature_importances"].to_string())
        f.write("\n\n模型参数：\n")
        for k, v in RF_PARAMS.items():
            f.write(f"  {k}: {v}\n")
    print(f"      报告：{REPORT_PATH}")


def demo_inference(model, feature_cols: list) -> None:
    """演示单条推理"""
    print("\n── 推理演示 ──────────────────────────────────────────────")
    sample = {
        "sprint_days": 14,
        "days_elapsed": 7,
        "committed_sp": 60.0,
        "remaining_sp": 25.0,
        "completed_sp": 35.0,
        "velocity_current": 5.0,
        "velocity_avg_5": 4.8,
        "velocity_std_5": 0.8,
        "blocked_stories": 1,
        "attendance_rate": 0.9,
        "ratio_feature": 0.6,
        "ratio_bug": 0.2,
        "ratio_tech_debt": 0.2,
        # 衍生特征
        "days_remaining": 7,
        "elapsed_ratio": 0.5,
        "remaining_ratio": 0.417,
        "velocity_gap": 0.2,
        "projected_sp": 70.0,
        "projected_completion_ratio": 1.17,
    }
    X_sample = np.array([[sample[c] for c in feature_cols]])
    prob = model.predict_proba(X_sample)[0][1]
    risk = "GREEN" if prob >= 0.8 else ("YELLOW" if prob >= 0.5 else "RED")
    print(f"  完成概率：{prob:.4f}  风险等级：{risk}")
    print("──────────────────────────────────────────────────────────\n")


if __name__ == "__main__":
    # 加载 & 特征工程
    df = load_data(DATA_PATH)
    df = engineer_features(df)

    # 最终特征列（原始 + 衍生）
    extended_cols = FEATURE_COLS + [
        "days_remaining",
        "elapsed_ratio",
        "remaining_ratio",
        "velocity_gap",
        "projected_sp",
        "projected_completion_ratio",
    ]

    # 划分 / 训练 / 评估
    X_train, X_test, y_train, y_test = split_data(df, extended_cols)
    model = train_model(X_train, y_train)
    cross_validate(model, X_train, y_train)
    eval_result = evaluate(model, X_test, y_test, extended_cols)

    # 保存图表 & 产物
    save_figures(model, X_test, y_test, eval_result, extended_cols)
    save_artifacts(model, extended_cols, eval_result)
    demo_inference(model, extended_cols)

    print("全部完成！")
