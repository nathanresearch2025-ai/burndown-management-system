"""
生成模拟任务工时数据
生成 1000 条训练数据，模拟真实的任务工时场景
"""

import pandas as pd
import numpy as np
from datetime import datetime, timedelta

# 设置随机种子，保证可复现
np.random.seed(42)

def generate_task_data(n_samples=1000):
    """
    生成模拟的任务工时数据

    参数:
        n_samples: 生成的样本数量

    返回:
        DataFrame: 包含任务特征和实际工时的数据
    """

    # 1. 任务类型（STORY, BUG, TASK, EPIC）
    task_types = np.random.choice(
        ['STORY', 'BUG', 'TASK', 'EPIC'],
        size=n_samples,
        p=[0.4, 0.3, 0.25, 0.05]  # 概率分布
    )

    # 2. 优先级（CRITICAL, HIGH, MEDIUM, LOW）
    priorities = np.random.choice(
        ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'],
        size=n_samples,
        p=[0.1, 0.3, 0.4, 0.2]
    )

    # 3. 故事点（1, 2, 3, 5, 8, 13）- 斐波那契数列
    story_points = np.random.choice(
        [1, 2, 3, 5, 8, 13],
        size=n_samples,
        p=[0.15, 0.25, 0.25, 0.2, 0.1, 0.05]
    )

    # 4. 标题长度（10-100 字符）
    title_lengths = np.random.randint(10, 100, size=n_samples)

    # 5. 描述长度（0-500 字符，30% 的任务没有描述）
    description_lengths = np.where(
        np.random.rand(n_samples) < 0.3,
        0,  # 30% 没有描述
        np.random.randint(50, 500, size=n_samples)
    )

    # 6. 标签数量（0-5 个）
    labels_count = np.random.choice([0, 1, 2, 3, 4, 5], size=n_samples, p=[0.2, 0.3, 0.25, 0.15, 0.07, 0.03])

    # 7. 创建月份（1-12）
    created_months = np.random.randint(1, 13, size=n_samples)

    # 8. 创建季度（1-4）
    created_quarters = (created_months - 1) // 3 + 1

    # 9. 创建星期几（0-6）
    created_day_of_week = np.random.randint(0, 7, size=n_samples)

    # 10. 距离截止日期天数（1-90 天）
    days_to_due = np.random.randint(1, 91, size=n_samples)

    # 11. 负责人 ID（模拟 10 个开发者）
    assignee_ids = np.random.randint(1, 11, size=n_samples)

    # 12. 项目 ID（模拟 5 个项目）
    project_ids = np.random.randint(1, 6, size=n_samples)

    # 构建 DataFrame
    df = pd.DataFrame({
        'task_type': task_types,
        'priority': priorities,
        'story_points': story_points,
        'title_length': title_lengths,
        'description_length': description_lengths,
        'labels_count': labels_count,
        'created_month': created_months,
        'created_quarter': created_quarters,
        'created_day_of_week': created_day_of_week,
        'days_to_due': days_to_due,
        'assignee_id': assignee_ids,
        'project_id': project_ids
    })

    # 13. 生成实际工时（目标变量）
    # 基于特征的真实关系生成工时
    actual_hours = generate_actual_hours(df)
    df['actual_hours'] = actual_hours

    return df


def generate_actual_hours(df):
    """
    基于特征生成实际工时（模拟真实关系）

    规则：
    - 故事点是主要因素（1 故事点 ≈ 4-6 小时）
    - 任务类型影响：EPIC > STORY > TASK > BUG
    - 优先级影响：CRITICAL 和 HIGH 通常工时更长（因为更复杂）
    - 描述长度：描述越长，任务越复杂
    - 添加随机噪声（±20%）
    """

    # 基础工时：故事点 × 5 小时
    base_hours = df['story_points'] * 5

    # 任务类型系数
    type_multiplier = df['task_type'].map({
        'EPIC': 1.5,
        'STORY': 1.2,
        'TASK': 1.0,
        'BUG': 0.8
    })

    # 优先级系数
    priority_multiplier = df['priority'].map({
        'CRITICAL': 1.3,
        'HIGH': 1.15,
        'MEDIUM': 1.0,
        'LOW': 0.9
    })

    # 描述长度影响（描述越长，工时增加 0-20%）
    description_factor = 1 + (df['description_length'] / 500) * 0.2

    # 计算工时
    hours = base_hours * type_multiplier * priority_multiplier * description_factor

    # 添加随机噪声（±20%）
    noise = np.random.uniform(0.8, 1.2, size=len(df))
    hours = hours * noise

    # 确保工时在合理范围内（1-100 小时）
    hours = np.clip(hours, 1, 100)

    # 四舍五入到小数点后 1 位
    return np.round(hours, 1)


if __name__ == '__main__':
    print("开始生成模拟数据...")

    # 生成 1000 条数据
    df = generate_task_data(n_samples=1000)

    # 保存到 CSV
    output_path = 'training_data.csv'
    df.to_csv(output_path, index=False)

    print(f"✅ 成功生成 {len(df)} 条数据")
    print(f"✅ 数据已保存到: {output_path}")
    print(f"\n数据预览：")
    print(df.head(10))
    print(f"\n数据统计：")
    print(df.describe())
    print(f"\n目标变量（actual_hours）分布：")
    print(f"  最小值: {df['actual_hours'].min():.1f} 小时")
    print(f"  最大值: {df['actual_hours'].max():.1f} 小时")
    print(f"  平均值: {df['actual_hours'].mean():.1f} 小时")
    print(f"  中位数: {df['actual_hours'].median():.1f} 小时")
