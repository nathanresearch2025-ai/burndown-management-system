# Sprint 完成概率预测（随机森林）简版需求

## 1. 需求背景

在 Scrum 实践中，PO / Scrum Master 常在 Sprint 进行中希望提前判断“是否能按时完成承诺的全部 Story Points”。

本需求旨在引入一个 **二分类预警模型**，在 Sprint 进行中输出“完成概率 / 延期风险”，用于站会和中期评审的决策支持。

## 2. 目标

- 在 Sprint 中期给出“完成概率”
- 输出红黄绿风险提示
- 支持在 Standup / Review 场景中触发

## 3. 模型选择

- 算法：**RandomForestClassifier（随机森林）**
- 理由：实现简单、可解释、抗噪声能力强、训练成本低

## 4. 预测目标（Label）

- `1 = Sprint 完成`（完成所有承诺 Story Points）
- `0 = Sprint 未完成`（仍有剩余或延期）

## 5. 特征定义（Feature）

从历史 Sprint + 当前 Sprint 中提取：

1. 已消耗天数 / 总 Sprint 天数
2. 当前剩余 Story Points / 初始承诺 Story Points
3. 当前实际速度（已完成 SP / 已消耗天数）
4. 最近 3~5 个 Sprint 平均 Velocity
5. 最近 3~5 个 Sprint Velocity 波动率（标准差）
6. 当前 Sprint 内阻断 / 返工 Story 数量
7. 团队成员出勤率（或请假天数）
8. Story 类型分布（新功能 / 技术债 / Bug 比例）

## 6. 模型输出

- 完成概率（如：`0.32`）
- 延期风险概率（如：`0.68`）
- 风险等级：
  - `GREEN`：完成概率 >= 0.8
  - `YELLOW`：完成概率 0.5 ~ 0.8
  - `RED`：完成概率 < 0.5

## 7. 业务价值

- Daily Standup / Mid-Sprint Review 中触发预警
- 支持提前调整 Scope 或人力
- 降低 Sprint 延期率，增强交付确定性

## 8. 使用时机

- Sprint 第 3~5 天之后开始预测
- 每天自动更新一次
- 也可手动触发

## 9. 系统集成（建议）

- 后端新增接口：`GET /api/v1/sprint/{id}/completion-probability`
- 返回：概率 + 风险等级 + 关键影响特征摘要
- Standup Agent 可调用并输出自然语言解释

## 10. 训练与更新

- 使用历史 Sprint 数据构建训练集
- 每季度或每 5~10 个 Sprint 更新模型
- MVP 可离线训练 + 线上推理

## 11. 复杂度评估

- 实现复杂度：低
- Python/sklearn 训练即可
- 推理可部署为独立服务或集成到后端

（本文件为简版需求，可用于立项与迭代规划。）
