# 随机森林 Sprint 完成预测需求文档（本地训练版）

## 1. 背景

Scrum 团队需要在 Sprint 中期对“是否能完成承诺的 Story Points”做出判断。
通过基于历史 Sprint 数据训练二分类模型，可在站会或中期评审阶段提前提示风险。

本需求面向“本地 Python 训练 + 后端线上推理”模式。

---

## 2. 目标

- 训练一个二分类模型，输出 Sprint 完成概率
- 输出风险等级（红/黄/绿）
- 在后端提供预测接口供站会助手调用

---

## 3. 预测目标（Label）

- `1 = Sprint 完成`（完成所有承诺 SP）
- `0 = Sprint 未完成`（仍有剩余或延期）

---

## 4. 特征定义（Feature）

从历史 Sprint + 当前 Sprint 计算：

1. 已消耗天数 / 总 Sprint 天数
2. 当前剩余 SP / 初始承诺 SP
3. 当前实际速度（已完成 SP / 已消耗天数）
4. 最近 3~5 个 Sprint 平均 Velocity
5. 最近 3~5 个 Sprint Velocity 波动率（标准差）
6. 当前 Sprint 内阻断/返工 Story 数量
7. 团队成员出勤率（或请假天数）
8. Story 类型分布（新功能/技术债/bug 比例）

---

## 5. 训练数据来源

- Sprint 主表：sprint 信息（开始/结束、承诺 SP）
- 任务表：Story Points、类型、状态
- 工时/请假表：出勤率、缺勤天数
- Burndown/Worklog：进度与速度

> 数据以 CSV 形式导出到本地进行训练。

---

## 6. 模型与训练方式

- 算法：RandomForestClassifier
- 训练方式：本地 Python 离线训练
- 训练频率：每 5~10 个 Sprint 更新一次模型

---

## 7. 输出与解释

- 输出：完成概率（0~1）
- 风险等级：
  - `GREEN`：>= 0.8
  - `YELLOW`：0.5 ~ 0.8
  - `RED`：< 0.5

额外输出（可选）：
- Top 3 影响特征（feature importance）

---

## 8. 接入点（建议）

新增后端接口：
- `GET /api/v1/sprints/{id}/completion-probability`

返回：
- `probability`
- `riskLevel`
- `featureSummary`

---

## 9. 验收标准

1. 模型可在本地完成训练并保存
2. 对历史 Sprint 可回放预测结果
3. 后端接口能返回概率与风险等级
4. Standup Agent 能调用并生成解释性回答

---

（本文件为随机森林本地训练需求文档。）
