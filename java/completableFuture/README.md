# CompletableFuture 简单需求文档（结合现有后端）

## 1. 背景
当前站会问答链路需要同时获取“进行中任务列表”和“燃尽图数据”。这两个数据源互不依赖，适合并行查询以降低接口耗时。

## 2. 需求描述
在 `StandupAgentService.query()` 中并行拉取：

- 进行中任务（`StandupTaskTools.getInProgressTasks`）
- Sprint 燃尽图（`StandupBurndownTools.getSprintBurndown`）

待两者返回后，再由 AI 生成摘要输出。

## 3. 目标
- 降低站会问答平均响应时间
- 保持现有接口与数据模型不变
- 不影响现有工具逻辑

## 4. 简单流程
1. 用户请求进入 `StandupAgentService.query()`
2. 使用 `CompletableFuture.supplyAsync` 并行请求任务与燃尽图
3. 使用 `thenCombine` 合并结果
4. 组合提示词并交给模型生成摘要
5. 返回最终响应

## 5. 预期收益
- 减少串行等待带来的延迟
- 避免引入复杂的异步框架
- 可逐步扩展到风险评估等更多并行任务
