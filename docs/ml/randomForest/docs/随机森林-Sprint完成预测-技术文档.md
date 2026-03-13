# 随机森林 Sprint 完成预测技术文档（本地训练版）

## 1. 总体流程

1. 从数据库导出 Sprint 历史数据为 CSV
2. 本地 Python 训练 RandomForestClassifier
3. 保存模型为 `joblib` 文件
4. 后端加载模型并对当前 Sprint 做推理
5. 返回概率与风险等级

---

## 2. 数据集设计

推荐训练数据字段（CSV）：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| sprint_id | int | Sprint ID |
| sprint_days | int | Sprint 总天数 |
| days_elapsed | int | 已消耗天数 |
| committed_sp | float | 初始承诺 SP |
| remaining_sp | float | 当前剩余 SP |
| completed_sp | float | 当前已完成 SP |
| velocity_current | float | 当前速度（completed_sp / days_elapsed） |
| velocity_avg_5 | float | 最近 5 个 Sprint 平均速度 |
| velocity_std_5 | float | 最近 5 个 Sprint 速度标准差 |
| blocked_stories | int | 阻断/返工 Story 数量 |
| attendance_rate | float | 出勤率 |
| ratio_feature | float | 新功能比例 |
| ratio_bug | float | bug 比例 |
| ratio_tech_debt | float | 技术债比例 |
| label_completed | int | 1=完成，0=未完成 |

---

## 3. 本地训练脚本（示例流程）

1. 读取 CSV
2. 处理缺失值
3. 划分训练/验证集
4. 训练 RandomForestClassifier
5. 评估准确率与 AUC
6. 保存模型

训练产物：
- `random_forest_model.pkl`
- `feature_columns.json`

---

## 4. 推理流程（后端）

1. 获取当前 Sprint 数据
2. 计算特征向量（与训练字段一致）
3. 调用模型预测概率
4. 映射风险等级
5. 返回给前端或 Agent

风险映射：
- `p >= 0.8` → GREEN
- `0.5 <= p < 0.8` → YELLOW
- `p < 0.5` → RED

---

## 5. 接口设计（建议）

`GET /api/v1/sprints/{id}/completion-probability`

响应示例：

```json
{
  "probability": 0.32,
  "riskLevel": "RED",
  "featureSummary": {
    "daysElapsedRatio": 0.5,
    "remainingRatio": 0.62,
    "velocityCurrent": 3.4,
    "velocityAvg": 4.2
  }
}
```

---

## 6. 部署与更新

- 模型文件存放路径：`/models/random_forest_model.pkl`
- 每 5~10 个 Sprint 重新训练更新
- 更新后重启服务加载新模型

---

## 7. 评估指标（推荐）

- Accuracy
- Precision / Recall
- ROC-AUC

---

（本文件为随机森林本地训练技术文档。）
