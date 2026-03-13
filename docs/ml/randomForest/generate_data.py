"""
生成 Sprint 完成预测的随机训练数据（1000条）
输出文件：sprint_training_data.csv
"""

import numpy as np
import pandas as pd

np.random.seed(42)
N = 1000

sprint_id = np.arange(1, N + 1)

# Sprint 总天数（10~30天）
sprint_days = np.random.randint(10, 31, N)

# 已消耗天数（1 ~ sprint_days）
days_elapsed = np.array([np.random.randint(1, d + 1) for d in sprint_days])

# 初始承诺 SP（20~100）
committed_sp = np.round(np.random.uniform(20, 100, N), 1)

# 已完成 SP（0 ~ committed_sp）
progress_ratio = np.random.beta(2, 2, N)  # 集中在中间
completed_sp = np.round(progress_ratio * committed_sp, 1)

# 剩余 SP = committed - completed（允许少量超出，不低于0）
remaining_sp = np.round(np.clip(committed_sp - completed_sp, 0, committed_sp), 1)

# 当前速度 = completed_sp / days_elapsed（days_elapsed>0保证）
velocity_current = np.round(completed_sp / np.maximum(days_elapsed, 1), 2)

# 最近5个Sprint平均速度（1.0~10.0）
velocity_avg_5 = np.round(np.random.uniform(1.0, 10.0, N), 2)

# 最近5个Sprint速度标准差（0~3）
velocity_std_5 = np.round(np.random.uniform(0.0, 3.0, N), 2)

# 阻断/返工 Story 数量（0~10）
blocked_stories = np.random.randint(0, 11, N)

# 出勤率（0.5~1.0）
attendance_rate = np.round(np.random.uniform(0.5, 1.0, N), 2)

# 任务类型比例（feature + bug + tech_debt <= 1.0）
ratio_feature = np.round(np.random.uniform(0.2, 0.7, N), 2)
ratio_bug = np.round(np.random.uniform(0.0, 0.4, N), 2)
# 确保不超过1
ratio_bug = np.where(ratio_feature + ratio_bug > 0.95, np.round(0.95 - ratio_feature, 2), ratio_bug)
ratio_bug = np.clip(ratio_bug, 0, 1)
ratio_tech_debt = np.round(np.clip(1.0 - ratio_feature - ratio_bug, 0, 0.5), 2)

# ---- 生成标签（基于业务逻辑的规则打分）----
days_remaining = sprint_days - days_elapsed
# 剩余天数内按当前速度能否完成
projected_completion = completed_sp + velocity_current * days_remaining
completion_score = np.clip(projected_completion / np.maximum(committed_sp, 1), 0, 2)

# 阻断惩罚
block_penalty = blocked_stories * 0.05

# 出勤率奖励
attendance_bonus = (attendance_rate - 0.75) * 0.4

# 速度与历史对比
velocity_ratio = np.where(
    velocity_avg_5 > 0,
    velocity_current / velocity_avg_5,
    1.0
)

# 综合得分（越高越可能完成）
score = (
    0.45 * completion_score
    + 0.25 * np.clip(velocity_ratio, 0, 2) / 2
    + 0.15 * attendance_rate
    - 0.15 * block_penalty
    + attendance_bonus * 0.1
)

# 加入噪声，提高真实感
noise = np.random.normal(0, 0.08, N)
score = score + noise

# 转为标签（阈值0.55）
label_completed = (score >= 0.55).astype(int)

df = pd.DataFrame({
    "sprint_id": sprint_id,
    "sprint_days": sprint_days,
    "days_elapsed": days_elapsed,
    "committed_sp": committed_sp,
    "remaining_sp": remaining_sp,
    "completed_sp": completed_sp,
    "velocity_current": velocity_current,
    "velocity_avg_5": velocity_avg_5,
    "velocity_std_5": velocity_std_5,
    "blocked_stories": blocked_stories,
    "attendance_rate": attendance_rate,
    "ratio_feature": ratio_feature,
    "ratio_bug": ratio_bug,
    "ratio_tech_debt": ratio_tech_debt,
    "label_completed": label_completed,
})

output_path = "sprint_training_data.csv"
df.to_csv(output_path, index=False)

print(f"已生成 {len(df)} 条数据 -> {output_path}")
print(f"完成率分布：\n{df['label_completed'].value_counts().to_string()}")
print(f"\n数据预览：\n{df.head(3).to_string()}")
