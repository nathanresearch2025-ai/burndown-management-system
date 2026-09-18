# 机器学习应用场景文档

本文档基于 Burndown Management System 后端代码，梳理已实现和可扩展的机器学习应用场景。

---

## 目录

1. [已实现场景](#已实现场景)
2. [可扩展场景](#可扩展场景)
3. [技术栈总结](#技术栈总结)
4. [实施建议](#实施建议)

---

## 已实现场景

### 1. Sprint 完成概率预测

**业务场景**
在 Sprint 进行过程中，预测该 Sprint 能否按时完成所有承诺的故事点，帮助 Scrum Master 和团队提前识别风险并采取干预措施。

**机器学习方法**
- **算法**：随机森林（Random Forest）分类器
- **模型类型**：监督学习 - 二分类（完成/未完成）
- **特征工程**（19个特征）：
  - 时间特征：Sprint 持续天数、已消耗天数、剩余天数、时间进度比例
  - 容量特征：承诺故事点、已完成故事点、剩余故事点、完成比例
  - 速度特征：当前速度、历史平均速度、速度标准差、速度差异
  - 任务特征：阻断任务数、任务类型分布（Feature/Bug/TechDebt）、团队出勤率
  - 衍生特征：预测最终完成故事点、预测完成比例

**技术实现**
- **训练**：Python + scikit-learn（离线训练，模型保存为 `.pkl` 文件）
- **推理**：Java 通过 `ProcessBuilder` 调用 Python 脚本进行实时预测
- **输出**：完成概率 [0.0, 1.0] + 风险等级（GREEN/YELLOW/RED）

**代码位置**
- 服务类：`SprintPredictionService.java`
- 控制器：`SprintPredictionController.java`
- Python 模型服务：`PythonModelService.java`
- 特征配置：`src/main/resources/models/feature_columns.json`

**API 端点**
```
GET /api/v1/sprints/{sprintId}/prediction
```

**应用价值**
- 提前 3-5 天预警 Sprint 风险
- 量化风险等级，辅助决策（加班、砍需求、延期）
- 基于历史数据持续优化预测准确率

---

### 2. 任务描述智能生成（RAG）

**业务场景**
创建新任务时，根据任务标题、类型、优先级等信息，自动生成详细的任务描述，包括目标、实现要点和验收标准，减少手动编写工作量。

**机器学习方法**
- **算法**：检索增强生成（RAG - Retrieval-Augmented Generation）
- **模型类型**：生成式 AI + 向量检索
- **工作流程**：
  1. **向量化**：使用 Embedding 模型（如 `text-embedding-3-small`）将任务标题、描述、类型、优先级转换为 1536 维向量
  2. **相似度检索**：在 PostgreSQL + pgvector 中查询最相似的历史任务（余弦相似度）
  3. **上下文构建**：将相似任务作为参考示例，构建提示词
  4. **生成**：调用 LLM（如 GPT-4、Claude）生成任务描述

**技术实现**
- **向量数据库**：PostgreSQL + pgvector 扩展
- **Embedding 服务**：`EmbeddingService.java`（调用 OpenAI 兼容 API）
- **RAG 服务**：`TaskAiService.java`
- **降级策略**：
  - 向量检索失败 → 关键词匹配
  - LLM 调用失败 → 模板生成

**代码位置**
- 服务类：`TaskAiService.java`、`EmbeddingService.java`、`AiClientService.java`
- 控制器：`TaskAiController.java`
- 实体：`Task.java`（包含 `embedding` 字段）
- 日志：`AiTaskGenerationLog.java`（记录生成历史和用户反馈）

**API 端点**
```
POST /api/v1/tasks/ai/generate-description
POST /api/v1/tasks/ai/feedback
```

**应用价值**
- 减少 60% 的任务描述编写时间
- 保持团队任务描述风格一致性
- 通过用户反馈持续优化生成质量

---

## 可扩展场景

### 3. 任务工时预测

**业务场景**
根据任务标题、类型、优先级、故事点等信息，预测该任务实际需要的工时（小时），帮助团队更准确地进行容量规划。

**机器学习方法**
- **算法**：梯度提升树（XGBoost/LightGBM）或神经网络
- **模型类型**：监督学习 - 回归
- **特征工程**：
  - 任务属性：类型、优先级、故事点、标题长度、描述长度
  - 历史数据：相似任务的平均工时、工时标准差
  - 团队特征：负责人的历史平均速度、团队规模
  - 时间特征：创建时间（季度、月份）、截止日期压力

**训练数据来源**
- `work_logs` 表：`time_spent` 字段（实际工时）
- `tasks` 表：任务属性

**技术实现建议**
```java
@Service
public class TaskEffortPredictionService {
    public BigDecimal predictEffort(Task task) {
        // 1. 特征提取
        Map<String, Double> features = extractFeatures(task);

        // 2. 调用 Python 模型
        double predictedHours = pythonModelService.predictTaskEffort(features);

        // 3. 返回预测工时
        return BigDecimal.valueOf(predictedHours);
    }
}
```

**应用价值**
- 提高 Sprint 容量规划准确性
- 减少工时估算偏差（通常人工估算误差 ±30%）
- 识别高风险任务（预测工时远超故事点）

---

### 4. 开发者技能匹配推荐

**业务场景**
创建新任务时，根据任务内容和开发者的历史表现，推荐最合适的负责人（assignee），提高任务分配效率和完成质量。

**机器学习方法**
- **算法**：协同过滤（Collaborative Filtering）+ 内容推荐（Content-Based）
- **模型类型**：推荐系统
- **特征工程**：
  - 任务特征：类型、优先级、技术标签（labels 字段）
  - 开发者特征：
    - 历史完成任务的类型分布
    - 平均完成速度（故事点/天）
    - 任务完成率（DONE 状态占比）
    - 技能标签匹配度
  - 协同特征：相似开发者的任务偏好

**训练数据来源**
- `tasks` 表：`assignee_id`、`type`、`labels`、`status`
- `work_logs` 表：开发者的工作时长分布
- `users` 表：开发者基础信息

**技术实现建议**
```java
@Service
public class TaskAssignmentRecommendationService {
    public List<UserRecommendation> recommendAssignees(Task task, int topK) {
        // 1. 提取任务特征向量
        PGvector taskEmbedding = embeddingService.generateEmbedding(
            task.getTitle() + " " + task.getDescription()
        );

        // 2. 计算所有开发者的匹配分数
        List<User> candidates = userRepository.findByProjectId(task.getProjectId());
        List<UserRecommendation> recommendations = candidates.stream()
            .map(user -> {
                double score = calculateMatchScore(task, user, taskEmbedding);
                return new UserRecommendation(user, score);
            })
            .sorted(Comparator.comparingDouble(UserRecommendation::score).reversed())
            .limit(topK)
            .toList();

        return recommendations;
    }
}
```

**应用价值**
- 减少任务分配时间（从 5 分钟降至 30 秒）
- 提高任务完成质量（匹配度高的开发者完成更快）
- 平衡团队工作负载

---

### 5. 燃尽图异常检测

**业务场景**
实时监控 Sprint 燃尽图，检测异常模式（如进度停滞、突然下降、波动剧烈），自动触发告警并分析原因。

**机器学习方法**
- **算法**：时间序列异常检测（Isolation Forest / LSTM Autoencoder）
- **模型类型**：无监督学习 - 异常检测
- **特征工程**：
  - 时间序列特征：每日剩余故事点、完成速度、速度变化率
  - 统计特征：滑动窗口均值、标准差、趋势斜率
  - 上下文特征：工作日/周末、节假日、团队出勤率

**训练数据来源**
- `burndown_points` 表：每日燃尽图快照
- `sprints` 表：Sprint 状态和完成情况

**技术实现建议**
```java
@Service
public class BurndownAnomalyDetectionService {
    public AnomalyReport detectAnomalies(Long sprintId) {
        // 1. 获取燃尽图时间序列
        List<BurndownPoint> points = burndownPointRepository
            .findBySprintIdOrderByDateAsc(sprintId);

        // 2. 提取时间序列特征
        double[] timeSeries = points.stream()
            .mapToDouble(p -> p.getRemainingPoints().doubleValue())
            .toArray();

        // 3. 调用异常检测模型
        AnomalyResult result = pythonModelService.detectAnomalies(timeSeries);

        // 4. 分析异常原因
        String reason = analyzeAnomalyReason(result, points);

        return new AnomalyReport(result.isAnomaly(), result.score(), reason);
    }
}
```

**应用价值**
- 提前 2-3 天发现进度异常
- 自动化监控，减少人工盯盘
- 提供异常原因分析（如"连续 3 天无进展，可能存在阻塞任务"）

---

### 6. 缺陷预测（Bug Prediction）

**业务场景**
在代码提交或任务完成时，预测该任务引入缺陷的概率，帮助 QA 团队优先测试高风险模块。

**机器学习方法**
- **算法**：逻辑回归（Logistic Regression）或随机森林
- **模型类型**：监督学习 - 二分类（有缺陷/无缺陷）
- **特征工程**：
  - 任务特征：类型、优先级、故事点、描述复杂度
  - 代码特征：代码行数变化、文件修改数量、圈复杂度
  - 开发者特征：历史缺陷率、经验年限
  - 时间特征：开发时长、是否赶工（接近截止日期）

**训练数据来源**
- `tasks` 表：`type = 'BUG'` 的任务关联的原始任务（通过 `parent_id`）
- Git 提交记录（需要集成 Git API）

**技术实现建议**
```java
@Service
public class BugPredictionService {
    public BugRiskAssessment predictBugRisk(Task task) {
        // 1. 特征提取
        Map<String, Double> features = Map.of(
            "story_points", task.getStoryPoints().doubleValue(),
            "description_length", task.getDescription().length(),
            "developer_bug_rate", calculateDeveloperBugRate(task.getAssigneeId()),
            "time_pressure", calculateTimePressure(task)
        );

        // 2. 模型预测
        double bugProbability = pythonModelService.predictBugRisk(features);

        // 3. 风险等级映射
        String riskLevel = bugProbability > 0.7 ? "HIGH"
                         : bugProbability > 0.4 ? "MEDIUM" : "LOW";

        return new BugRiskAssessment(bugProbability, riskLevel);
    }
}
```

**应用价值**
- 优化测试资源分配（优先测试高风险任务）
- 减少生产环境缺陷逃逸率
- 提高代码质量意识

---

### 7. 团队速度预测（Velocity Forecasting）

**业务场景**
基于团队历史表现和当前状态，预测未来 1-3 个 Sprint 的开发速度（故事点/Sprint），辅助长期规划。

**机器学习方法**
- **算法**：时间序列预测（ARIMA / Prophet / LSTM）
- **模型类型**：监督学习 - 回归
- **特征工程**：
  - 历史速度：最近 5-10 个 Sprint 的速度序列
  - 季节性特征：季度、月份（考虑假期影响）
  - 团队特征：团队规模变化、新成员加入
  - 外部因素：项目复杂度、技术债务比例

**训练数据来源**
- `sprints` 表：`velocity` 字段（每个 Sprint 的实际速度）
- `tasks` 表：任务类型分布

**技术实现建议**
```java
@Service
public class VelocityForecastingService {
    public List<VelocityForecast> forecastVelocity(Long projectId, int futureSprints) {
        // 1. 获取历史速度数据
        List<Sprint> historicalSprints = sprintRepository
            .findTop10ByProjectIdAndStatusOrderByEndDateDesc(
                projectId, Sprint.SprintStatus.COMPLETED
            );

        // 2. 构建时间序列
        double[] velocities = historicalSprints.stream()
            .mapToDouble(s -> s.getVelocity().doubleValue())
            .toArray();

        // 3. 调用预测模型
        double[] predictions = pythonModelService.forecastVelocity(
            velocities, futureSprints
        );

        // 4. 构建预测结果
        return IntStream.range(0, futureSprints)
            .mapToObj(i -> new VelocityForecast(i + 1, predictions[i]))
            .toList();
    }
}
```

**应用价值**
- 提高长期规划准确性（Release Planning）
- 识别团队能力趋势（上升/下降）
- 辅助资源调配决策

---

### 8. 智能站会摘要生成（已部分实现）

**业务场景**
基于任务进展、燃尽图、工作日志等数据，自动生成每日站会摘要，包括进展、风险、阻塞点和建议。

**机器学习方法**
- **算法**：多 Agent 协作（Planner → Data Agent → Analyst → Writer）
- **模型类型**：生成式 AI + 工具调用（Function Calling）
- **工作流程**：
  1. **规划**：理解用户问题，制定数据采集计划
  2. **数据采集**：调用工具获取任务、燃尽图、风险评估数据
  3. **分析**：识别趋势、风险、异常
  4. **生成**：撰写结构化的站会摘要

**技术实现**
- **Spring AI 版本**：`StandupAgentService.java`（单 Agent + Function Calling）
- **LangChain 版本**：`agents.py`（多 Agent 流水线）

**代码位置**
- Spring AI：`com.burndown.aiagent.standup`
- LangChain：`backend/langchain-python/app/agents.py`

**应用价值**
- 减少站会准备时间（从 10 分钟降至 1 分钟）
- 标准化站会内容，避免遗漏关键信息
- 提供数据驱动的决策建议

---

## 技术栈总结

### 已使用技术

| 技术 | 用途 | 代码位置 |
|------|------|---------|
| **scikit-learn** | 随机森林模型训练（Sprint 预测） | Python 脚本 |
| **PostgreSQL + pgvector** | 向量存储和相似度检索 | `Task.embedding` 字段 |
| **OpenAI Embedding API** | 文本向量化（1536 维） | `EmbeddingService.java` |
| **LLM API（OpenAI/Claude）** | 任务描述生成、站会摘要 | `AiClientService.java` |
| **Spring AI** | Function Calling、Agent 编排 | `StandupAgentService.java` |
| **LangChain** | 多 Agent 流水线、ReAct 模式 | `agents.py` |
| **Micrometer + Prometheus** | ML 服务监控（请求数、延迟、成功率） | `StandupAgentMetrics.java` |

### 推荐扩展技术

| 技术 | 适用场景 | 优势 |
|------|---------|------|
| **XGBoost / LightGBM** | 工时预测、缺陷预测 | 高精度、可解释性强 |
| **Prophet** | 速度预测、时间序列分析 | 自动处理季节性和趋势 |
| **Isolation Forest** | 燃尽图异常检测 | 无需标注数据 |
| **Sentence Transformers** | 任务相似度计算 | 比 OpenAI Embedding 更快、更便宜 |
| **Redis** | 向量缓存、预测结果缓存 | 减少重复计算 |

---

## 实施建议

### 1. 优先级排序

根据业务价值和实施难度，建议按以下顺序实施：

| 优先级 | 场景 | 业务价值 | 实施难度 | 预计周期 |
|--------|------|---------|---------|---------|
| **P0** | Sprint 完成预测 | ⭐⭐⭐⭐⭐ | 🔧🔧 | 已完成 |
| **P0** | 任务描述生成（RAG） | ⭐⭐⭐⭐ | 🔧🔧🔧 | 已完成 |
| **P1** | 任务工时预测 | ⭐⭐⭐⭐ | 🔧🔧 | 1-2 周 |
| **P1** | 燃尽图异常检测 | ⭐⭐⭐⭐ | 🔧🔧🔧 | 2-3 周 |
| **P2** | 开发者技能匹配 | ⭐⭐⭐ | 🔧🔧🔧🔧 | 3-4 周 |
| **P2** | 团队速度预测 | ⭐⭐⭐ | 🔧🔧 | 1-2 周 |
| **P3** | 缺陷预测 | ⭐⭐ | 🔧🔧🔧🔧 | 4-6 周 |

### 2. 数据准备

所有 ML 场景都依赖高质量的历史数据：

- **最小数据量**：至少 50 个已完成的 Sprint（Sprint 预测）
- **数据清洗**：处理缺失值、异常值（如负数工时）
- **标注数据**：缺陷预测需要标注哪些任务引入了 Bug
- **特征工程**：提前计算衍生特征并存储（如开发者历史速度）

### 3. 模型训练流程

```bash
# 1. 数据导出（从 PostgreSQL 导出训练数据）
python scripts/export_training_data.py --output data/sprint_data.csv

# 2. 模型训练
python scripts/train_sprint_model.py --input data/sprint_data.csv --output models/sprint_rf.pkl

# 3. 模型评估
python scripts/evaluate_model.py --model models/sprint_rf.pkl --test data/test_data.csv

# 4. 部署模型（复制到 Spring Boot resources 目录）
cp models/sprint_rf.pkl backend/src/main/resources/models/
```

### 4. 监控和优化

- **模型性能监控**：
  - 预测准确率（Accuracy、F1-Score）
  - 预测延迟（P50、P95、P99）
  - 降级率（Fallback Rate）

- **A/B 测试**：
  - 对比新旧模型的预测效果
  - 收集用户反馈（如任务描述生成的接受率）

- **模型更新策略**：
  - 每月重新训练一次（使用最新数据）
  - 监控模型漂移（Drift Detection）

### 5. 成本控制

| 成本项 | 估算 | 优化建议 |
|--------|------|---------|
| **LLM API 调用** | $0.01-0.05/次 | 使用 Redis 缓存相似请求 |
| **Embedding API** | $0.0001/1K tokens | 批量生成、本地缓存 |
| **向量存储** | 1536 维 × 10K 任务 ≈ 60MB | 定期清理旧任务向量 |
| **模型训练** | 本地 GPU 或云端 | 使用 AutoML 减少调参时间 |

---

## 总结

Burndown Management System 已经实现了 **2 个核心 ML 场景**（Sprint 预测、任务描述生成），并具备扩展 **6 个高价值场景** 的技术基础。

**关键成功因素**：
1. **数据质量**：确保历史数据完整、准确
2. **渐进式实施**：从简单场景（工时预测）到复杂场景（缺陷预测）
3. **用户反馈闭环**：持续收集反馈优化模型
4. **成本控制**：合理使用缓存和降级策略

**下一步行动**：
- 优先实施 **任务工时预测**（数据充足、价值高、难度低）
- 完善 **向量检索基础设施**（优化 pgvector 索引、增加缓存）
- 建立 **模型训练 Pipeline**（自动化数据导出、训练、评估、部署）
