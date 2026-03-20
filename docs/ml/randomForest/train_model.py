"""
随机森林 Sprint 完成预测 - 模型训练脚本
依赖：pip install scikit-learn pandas numpy matplotlib joblib
运行：python train_model.py
输出：random_forest_model.pkl  feature_columns.json  training_report.txt

脚本做的事（从数据到模型产物的端到端流程）：
- 读取训练数据 CSV，并对特征列做缺失值填充（用中位数）
- 进行特征工程（基于 sprint 进度/速度等生成衍生特征）
- 切分训练集/测试集（按标签分层抽样，保证类别比例一致）
- 训练 RandomForestClassifier，并用 5 折交叉验证评估 AUC 稳定性
- 在测试集上输出 Accuracy / ROC-AUC / 分类报告 / 特征重要性
- 保存模型与训练报告，并绘制 ROC、特征重要性、混淆矩阵
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

warnings.filterwarnings("ignore")

# ─── 配置 ────────────────────────────────────────────────────────────────────
# 训练数据文件路径：需包含 FEATURE_COLS 与 TARGET_COL
DATA_PATH = "sprint_training_data.csv"
# 输出：训练好的随机森林模型（joblib 序列化，便于后续推理加载）
MODEL_PATH = "random_forest_model.pkl"
# 输出：用于推理阶段保持特征列顺序一致（非常重要：否则会把特征喂错位置）
FEATURE_COLUMNS_PATH = "feature_columns.json"
# 输出：训练评估报告（便于留档与对比不同版本模型）
REPORT_PATH = "training_report.txt"
# 输出：训练图表（ROC/特征重要性/混淆矩阵）
FIGURES_DIR = "figures"

# 原始特征列（来自 CSV）
# 这些字段应当是“到当前日期为止”可观测到的 sprint 运行数据，
# 避免泄漏未来信息（比如 sprint 结束后的最终结果）。
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
# 目标标签列：二分类（0=未完成，1=完成）
TARGET_COL = "label_completed"

# 随机森林超参数
# - class_weight=balanced：当“完成/未完成”样本不均衡时，自动调整类权重，避免模型偏向多数类
# - max_features=sqrt：每棵树分裂时考虑的特征数为 sqrt(d)，常用于分类任务，能提升泛化
# - n_jobs=-1：并行训练多棵树（占用 CPU，换取训练速度）
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

def _running_in_notebook() -> bool:
    """
    粗略判断是否在 Jupyter Notebook / IPython kernel 中运行。

    用途：在 notebook 中默认“直接展示图”，在脚本模式下默认“保存到文件”。
    """
    try:
        from IPython import get_ipython  # type: ignore

        ip = get_ipython()
        if ip is None:
            return False
        # ZMQInteractiveShell 通常表示 Jupyter
        return ip.__class__.__name__ == "ZMQInteractiveShell"
    except Exception:
        return False


def load_data(path: str) -> pd.DataFrame:
    """
    读取训练数据并做最小清洗。

    - 输入：CSV 文件路径
    - 输出：DataFrame（确保 FEATURE_COLS 与 TARGET_COL 不含缺失值）
    - 缺失值策略：对特征列用“中位数”填充（对异常值更鲁棒），标签列一般不应缺失
    """
    print(f"[1/6] 加载数据：{path}")
    df = pd.read_csv(path)
    print(f"      行数={len(df)}，列数={len(df.columns)}")
    missing = df[FEATURE_COLS + [TARGET_COL]].isnull().sum()
    if missing.any():
        print(f"      缺失值检测：\n{missing[missing > 0]}")
        # 只对特征列填充：用每列中位数填补缺失（不会把缺失当作 0，从而引入偏差）
        df[FEATURE_COLS] = df[FEATURE_COLS].fillna(df[FEATURE_COLS].median())
    return df


def engineer_features(df: pd.DataFrame) -> pd.DataFrame:
    """
    添加衍生特征（Feature Engineering）。

    目标：把“是否能按期完成”相关的信号显式化，让模型更容易学习。
    注意：衍生特征必须只使用“当下可用信息”，不能使用未来信息，避免 label 泄漏。

    新增字段说明：
    - days_remaining：Sprint 剩余天数
    - elapsed_ratio：已过天数占比（进度条）
    - remaining_ratio：剩余工作量占比（remaining_sp / committed_sp）
    - velocity_gap：当前速度 vs 近期平均速度差
    - projected_sp：按当前速度外推可完成的 SP（粗略预测）
    - projected_completion_ratio：外推完成量占承诺量比例（>1 表示可能超额完成）
    """
    df = df.copy()
    # 剩余天数：为 0 或负数时代表 sprint 已接近结束或已超期（clip 用于防御式处理）
    df["days_remaining"] = df["sprint_days"] - df["days_elapsed"]
    # 已过比例：分母至少为 1，防止 sprint_days=0 导致除零
    df["elapsed_ratio"] = df["days_elapsed"] / df["sprint_days"].clip(lower=1)
    # 剩余 SP 占比：同样对 committed_sp 做下限裁剪，避免 committed_sp=0
    df["remaining_ratio"] = df["remaining_sp"] / df["committed_sp"].clip(lower=1)
    # 当前速度相对近期均值的偏差：正值可能代表状态好转，负值可能代表速度下降
    df["velocity_gap"] = df["velocity_current"] - df["velocity_avg_5"]
    # 简单外推：当前已完成 + 当前速度 * 剩余天数（并对剩余天数做下限裁剪）
    df["projected_sp"] = df["completed_sp"] + df["velocity_current"] * df["days_remaining"].clip(lower=0)
    # 外推完成比例：用来直接反映“按当前趋势是否能达到 committed_sp”
    df["projected_completion_ratio"] = df["projected_sp"] / df["committed_sp"].clip(lower=1)
    return df


def split_data(df: pd.DataFrame, feature_cols: list):
    """
    训练/测试集切分。

    - stratify=y：保持两类样本比例一致（避免测试集某一类过少导致指标不稳定）
    - random_state：保证可复现
    """
    X = df[feature_cols].values
    y = df[TARGET_COL].values
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, stratify=y, random_state=42
    )
    print(f"[2/6] 数据划分：训练={len(X_train)}，测试={len(X_test)}")
    return X_train, X_test, y_train, y_test


def train_model(X_train, y_train) -> RandomForestClassifier:
    """
    训练随机森林分类器。

    随机森林适合：
    - 特征存在非线性关系
    - 不想对特征做严格分布假设
    - 需要一定可解释性（特征重要性）

    注意：树模型通常不需要标准化/归一化，所以本脚本不做 scaler。
    """
    print("[3/6] 训练 RandomForestClassifier ...")
    model = RandomForestClassifier(**RF_PARAMS)
    model.fit(X_train, y_train)
    print("      训练完成")
    return model


def cross_validate(model, X_train, y_train) -> None:
    """
    用交叉验证估计训练集上的泛化能力稳定性。

    - 使用 StratifiedKFold：每折仍保持类别比例
    - scoring=roc_auc：对不均衡分类更稳健（比 accuracy 更能反映排序能力）
    """
    print("[4/6] 5-折交叉验证 ...")
    cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)
    cv_scores = cross_val_score(model, X_train, y_train, cv=cv, scoring="roc_auc")
    print(f"      CV AUC：{cv_scores.round(4)}  均值={cv_scores.mean():.4f}  标准差={cv_scores.std():.4f}")


def evaluate(model, X_test, y_test, feature_cols: list) -> dict:
    """
    在测试集上评估模型。

    输出：
    - accuracy：整体正确率（受类别分布影响较大）
    - roc_auc：基于概率输出的排序指标（更适合二分类对比）
    - classification_report：精确率/召回率/F1（分别看“完成/未完成”）
    - feature_importances：随机森林的特征重要性（基于分裂带来的 impurity 降低）
    """
    print("[5/6] 评估测试集 ...")
    # 类别预测（0/1）
    y_pred = model.predict(X_test)
    # 概率预测：取正类（完成=1）概率，用于 AUC/ROC 计算与风险分级
    y_prob = model.predict_proba(X_test)[:, 1]

    acc = accuracy_score(y_test, y_pred)
    auc = roc_auc_score(y_test, y_prob)
    report = classification_report(y_test, y_pred, target_names=["未完成", "完成"])

    print(f"      Accuracy : {acc:.4f}")
    print(f"      ROC-AUC  : {auc:.4f}")
    print(f"\n{report}")

    # 特征重要性
    # 重要性越高代表该特征在树分裂中贡献越大；但注意：
    # - 对高基数/连续特征有偏好
    # - 相关特征之间会“分摊”重要性
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


def save_figures(
    model,
    X_test,
    y_test,
    eval_result: dict,
    feature_cols: list,
    *,
    show: bool | None = None,
    save: bool = True,
) -> plt.Figure:
    """
    生成训练可视化图表，便于快速理解模型表现。

    - ROC Curve：阈值从 0→1 的整体排序能力
    - Feature Importances：top 10 重要特征（便于解释与后续特征优化）
    - Confusion Matrix：看错分类型（把“完成”判成“未完成” vs 反过来）
    """
    # show=None 表示自动：notebook 里展示、脚本里不展示
    if show is None:
        show = _running_in_notebook()

    if save:
        os.makedirs(FIGURES_DIR, exist_ok=True)

    # ROC 曲线
    # fpr/tpr：假阳性率/真正率；AUC 越高越好（1.0 最佳，0.5≈随机）
    fpr, tpr, _ = roc_curve(y_test, eval_result["y_prob"])
    fig, axes = plt.subplots(1, 3, figsize=(18, 5))

    axes[0].plot(fpr, tpr, color="steelblue", lw=2, label=f"AUC = {eval_result['roc_auc']:.4f}")
    axes[0].plot([0, 1], [0, 1], "k--", lw=1)
    axes[0].set_xlabel("False Positive Rate")
    axes[0].set_ylabel("True Positive Rate")
    axes[0].set_title("ROC Curve")
    axes[0].legend(loc="lower right")

    # 特征重要性
    # 只画 top 10：避免图太长不易阅读
    fi = eval_result["feature_importances"].head(10)
    fi.sort_values().plot(kind="barh", ax=axes[1], color="steelblue")
    axes[1].set_title("Top 10 Feature Importances")
    axes[1].set_xlabel("Importance")

    # 混淆矩阵
    # 直观看出 FP/FN：例如把“未完成”误判为“完成”会导致风险低估
    ConfusionMatrixDisplay.from_predictions(
        y_test, eval_result["y_pred"],
        display_labels=["未完成", "完成"],
        ax=axes[2],
        colorbar=False,
    )
    axes[2].set_title("Confusion Matrix")

    plt.tight_layout()
    if save:
        fig_path = os.path.join(FIGURES_DIR, "training_summary.png")
        plt.savefig(fig_path, dpi=120)
        print(f"      图表已保存：{fig_path}")

    # notebook 中展示；脚本模式默认不展示以避免阻塞/弹窗
    if show:
        plt.show()
    else:
        plt.close(fig)

    return fig


def save_artifacts(model, feature_cols: list, eval_result: dict) -> None:
    """
    保存训练产物，确保线上推理可复现。

    - random_forest_model.pkl：模型本体
    - feature_columns.json：推理时必须使用同样的特征顺序构造输入向量
    - training_report.txt：评估指标、特征重要性、参数留档，便于版本对比
    """
    print("[6/6] 保存模型产物 ...")

    # 保存模型：joblib 对 sklearn 模型序列化更常用
    joblib.dump(model, MODEL_PATH)
    print(f"      模型：{MODEL_PATH}")

    # 保存特征列：推理阶段按照这个顺序构造 numpy 数组
    with open(FEATURE_COLUMNS_PATH, "w", encoding="utf-8") as f:
        json.dump({"feature_columns": feature_cols}, f, ensure_ascii=False, indent=2)
    print(f"      特征列：{FEATURE_COLUMNS_PATH}")

    # 保存报告：把关键指标和特征重要性写入文本，便于在 CI/评审中查看
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
    """
    演示单条推理（仅用于快速 sanity check）。

    注意：这里的 sample 需要包含“extended_cols”里所有特征（含衍生特征），
    且顺序必须与 feature_cols 一致，否则推理输入会错位。
    """
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
    # 按 feature_cols 的顺序组装成 2D 数组（形状：1 x d）
    X_sample = np.array([[sample[c] for c in feature_cols]])
    # 输出“完成概率”：越大代表越可能在 sprint 结束时完成承诺
    prob = model.predict_proba(X_sample)[0][1]
    # 仅做示意性的三段风险分级阈值（可按业务容忍度调整）
    risk = "GREEN" if prob >= 0.8 else ("YELLOW" if prob >= 0.5 else "RED")
    print(f"  完成概率：{prob:.4f}  风险等级：{risk}")
    print("──────────────────────────────────────────────────────────\n")


if __name__ == "__main__":
    # 1) 加载数据 & 特征工程（生成衍生特征）
    df = load_data(DATA_PATH)
    df = engineer_features(df)

    # 2) 最终特征列 = 原始特征 + 衍生特征
    #    训练与推理必须共享同一份特征列顺序（会输出到 feature_columns.json）
    extended_cols = FEATURE_COLS + [
        "days_remaining",
        "elapsed_ratio",
        "remaining_ratio",
        "velocity_gap",
        "projected_sp",
        "projected_completion_ratio",
    ]

    # 3) 划分 / 训练 / 交叉验证 / 测试集评估
    X_train, X_test, y_train, y_test = split_data(df, extended_cols)
    model = train_model(X_train, y_train)
    cross_validate(model, X_train, y_train)
    eval_result = evaluate(model, X_test, y_test, extended_cols)

    # 4) 保存图表 & 训练产物（模型/特征列/报告）
    # 脚本模式下：默认保存图片，不强制 show（避免弹窗/阻塞）
    save_figures(model, X_test, y_test, eval_result, extended_cols, show=False, save=True)
    save_artifacts(model, extended_cols, eval_result)
    demo_inference(model, extended_cols)

    print("全部完成！")
