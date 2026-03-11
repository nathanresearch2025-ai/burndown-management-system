package com.burndown.service;

import com.burndown.dto.SprintCompletionPredictionDto;
import com.burndown.entity.Sprint;
import com.burndown.entity.Task;
import com.burndown.exception.BusinessException;
import com.burndown.repository.SprintRepository;
import com.burndown.repository.TaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 完成预测服务
 * 使用训练好的随机森林模型进行预测
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SprintPredictionService {

    private final SprintRepository sprintRepository;
    private final TaskRepository taskRepository;
    private final ObjectMapper objectMapper;
    private final PythonModelService pythonModelService;

    private List<String> featureColumns;

    @PostConstruct
    public void init() {
        try {
            loadFeatureColumns();
            log.info("Sprint 预测服务初始化完成，特征列数: {}", featureColumns.size());
        } catch (Exception e) {
            log.error("Sprint 预测服务初始化失败", e);
        }
    }

    /**
     * 预测 Sprint 完成概率 - 核心业务方法
     *
     * @param sprintId Sprint 的唯一标识符
     * @return SprintCompletionPredictionDto 包含预测概率、风险等级和特征摘要的完整预测结果
     * @throws BusinessException 当 Sprint 不存在时抛出 404 异常
     */
    public SprintCompletionPredictionDto predictSprintCompletion(Long sprintId) {
        // 第1步：从数据库查询 Sprint 实体
        // 使用 Optional.orElseThrow() 确保 Sprint 存在，不存在则抛出业务异常
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new BusinessException("SPRINT_NOT_FOUND", "Sprint not found: " + sprintId, HttpStatus.NOT_FOUND));

        // 第2步：特征工程 - 计算机器学习模型所需的19个特征向量
        // 包括时间特征、容量特征、速度特征、任务特征等
        Map<String, Double> features = calculateFeatures(sprint);

        // 第3步：模型推理 - 调用 Python 随机森林模型进行预测
        // 通过 ProcessBuilder 执行 Python 脚本，传入特征向量，返回完成概率 [0.0, 1.0]
        double probability = pythonModelService.predictSprintCompletion(features, featureColumns);

        // 第4步：风险等级映射 - 将数值概率转换为业务可理解的风险等级
        // GREEN: >=0.8, YELLOW: 0.5-0.8, RED: <0.5
        String riskLevel = mapRiskLevel(probability);

        // 第5步：构建特征摘要 - 提取关键特征用于前端展示和业务分析
        // 包括时间进度、剩余工作量、速度对比等核心指标
        SprintCompletionPredictionDto.FeatureSummary featureSummary = buildFeatureSummary(features);

        // 第6步：构建并返回完整的预测结果对象
        // 使用 Builder 模式创建不可变的响应对象
        return SprintCompletionPredictionDto.builder()
                .probability(probability)           // 完成概率 [0.0, 1.0]
                .riskLevel(riskLevel)              // 风险等级 GREEN/YELLOW/RED
                .featureSummary(featureSummary)    // 特征摘要对象
                .predictedAt(System.currentTimeMillis())  // 预测时间戳（毫秒）
                .build();
    }

    /**
     * 计算 Sprint 特征向量 - 机器学习模型的核心输入
     *
     * 该方法从 Sprint 实体和相关数据中提取19个特征，分为4大类：
     * 1. 时间特征（4个）：Sprint 持续时间、已消耗时间、剩余时间、时间进度比例
     * 2. 容量特征（4个）：承诺故事点、已完成故事点、剩余故事点、完成比例
     * 3. 速度特征（4个）：当前速度、历史平均速度、速度标准差、速度差异
     * 4. 任务特征（7个）：阻断任务数、任务类型分布、出勤率等
     *
     * @param sprint Sprint 实体对象，包含基础的 Sprint 信息
     * @return Map<String, Double> 特征名称到特征值的映射，共19个特征
     */
    private Map<String, Double> calculateFeatures(Sprint sprint) {
        // 初始化特征向量容器
        Map<String, Double> features = new HashMap<>();

        // ==================== 第1类：基础时间特征 ====================
        // 获取当前日期作为计算基准点
        LocalDate now = LocalDate.now();

        // 计算 Sprint 总持续天数（从开始日期到结束日期）
        long totalDays = ChronoUnit.DAYS.between(sprint.getStartDate(), sprint.getEndDate());

        // 计算已消耗天数（从开始日期到当前日期）
        long elapsedDays = ChronoUnit.DAYS.between(sprint.getStartDate(), now);

        // 计算剩余天数（从当前日期到结束日期），确保不为负数
        long remainingDays = Math.max(0, ChronoUnit.DAYS.between(now, sprint.getEndDate()));

        // 存储时间相关特征
        features.put("sprint_days", (double) totalDays);                                    // Sprint 总天数
        features.put("days_elapsed", (double) Math.max(1, elapsedDays));                  // 已消耗天数（最小为1，避免除零）
        features.put("days_remaining", (double) remainingDays);                           // 剩余天数
        features.put("elapsed_ratio", elapsedDays / (double) Math.max(1, totalDays));    // 时间消耗比例 [0.0, 1.0]

        // ==================== 第2类：Sprint 容量特征 ====================
        // 从 Sprint 实体中提取故事点信息，处理 null 值并转换为 double
        double committedSp = sprint.getCommittedPoints() != null ? sprint.getCommittedPoints().doubleValue() : 0.0;  // 初始承诺的故事点
        double completedSp = sprint.getCompletedPoints() != null ? sprint.getCommittedPoints().doubleValue() : 0.0;  // 已完成的故事点
        double remainingSp = Math.max(0, committedSp - completedSp);                                                 // 剩余故事点（确保非负）

        // 存储容量相关特征
        features.put("committed_sp", committedSp);                                        // 承诺故事点总数
        features.put("completed_sp", completedSp);                                        // 已完成故事点数
        features.put("remaining_sp", remainingSp);                                        // 剩余故事点数
        features.put("remaining_ratio", committedSp > 0 ? remainingSp / committedSp : 0.0);  // 剩余工作比例

        // ==================== 第3类：速度特征 ====================
        // 计算当前 Sprint 的开发速度（故事点/天）
        double velocityCurrent = elapsedDays > 0 ? completedSp / elapsedDays : 0.0;

        // 从历史数据计算团队的平均速度和速度稳定性
        double velocityAvg = calculateHistoricalVelocity(sprint.getProjectId());          // 最近5个 Sprint 的平均速度
        double velocityStd = calculateVelocityStd(sprint.getProjectId());                 // 最近5个 Sprint 的速度标准差

        // 存储速度相关特征
        features.put("velocity_current", velocityCurrent);                                // 当前速度（故事点/天）
        features.put("velocity_avg_5", velocityAvg);                                      // 历史平均速度
        features.put("velocity_std_5", velocityStd);                                      // 速度标准差（稳定性指标）
        features.put("velocity_gap", velocityCurrent - velocityAvg);                      // 当前速度与历史平均的差异

        // ==================== 第4类：任务特征 ====================
        // 查询当前 Sprint 下的所有任务，用于分析任务状态和类型分布
        List<Task> sprintTasks = taskRepository.findBySprintId(sprint.getId());

        // 统计阻断状态的任务数量（BLOCKED 状态的 Story）
        // 阻断任务数量是影响 Sprint 完成的重要风险因子
        int blockedStories = (int) sprintTasks.stream()
                .filter(task -> "BLOCKED".equals(task.getStatus()))  // 过滤出状态为 BLOCKED 的任务
                .count();                                            // 计算数量

        // 存储阻断任务特征
        features.put("blocked_stories", (double) blockedStories);    // 阻断任务数量

        // 计算任务类型分布比例 - 不同类型任务的完成难度和风险不同
        long totalTasks = sprintTasks.size();                        // 总任务数
        if (totalTasks > 0) {
            // 统计新功能类型任务数量
            long featureTasks = sprintTasks.stream()
                    .filter(task -> "FEATURE".equals(task.getType()))
                    .count();

            // 统计缺陷修复类型任务数量
            long bugTasks = sprintTasks.stream()
                    .filter(task -> "BUG".equals(task.getType()))
                    .count();

            // 统计技术债务类型任务数量
            long techDebtTasks = sprintTasks.stream()
                    .filter(task -> "TECH_DEBT".equals(task.getType()))
                    .count();

            // 计算各类型任务的比例 [0.0, 1.0]
            features.put("ratio_feature", featureTasks / (double) totalTasks);      // 新功能任务比例
            features.put("ratio_bug", bugTasks / (double) totalTasks);              // Bug 修复任务比例
            features.put("ratio_tech_debt", techDebtTasks / (double) totalTasks);   // 技术债务任务比例
        } else {
            // 如果没有任务，所有比例设为 0
            features.put("ratio_feature", 0.0);
            features.put("ratio_bug", 0.0);
            features.put("ratio_tech_debt", 0.0);
        }

        // 团队出勤率特征（当前简化为固定值 0.85）
        // TODO: 实际应该从工作日志（work_logs 表）中计算真实出勤率
        // 出勤率影响团队的实际开发能力和 Sprint 完成概率
        features.put("attendance_rate", 0.85);

        // ==================== 第5类：衍生特征 ====================
        // 基于已有特征计算的高级特征，提供更强的预测能力

        // 预测最终完成的故事点数 = 当前已完成 + 按当前速度在剩余时间内能完成的
        double projectedSp = completedSp + velocityCurrent * remainingDays;

        // 存储衍生特征
        features.put("projected_sp", projectedSp);                                           // 预测最终完成故事点
        features.put("projected_completion_ratio", committedSp > 0 ? projectedSp / committedSp : 0.0);  // 预测完成比例

        // 返回包含19个特征的完整特征向量
        return features;
    }

    /**
     * 计算历史平均速度
     */
    private double calculateHistoricalVelocity(Long projectId) {
        List<Sprint> recentSprints = sprintRepository.findTop5ByProjectIdAndStatusOrderByEndDateDesc(
                projectId, Sprint.SprintStatus.COMPLETED);

        if (recentSprints.isEmpty()) {
            return 3.0; // 默认速度
        }

        return recentSprints.stream()
                .filter(s -> s.getVelocity() != null)
                .mapToDouble(s -> s.getVelocity().doubleValue())
                .average()
                .orElse(3.0);
    }

    /**
     * 计算速度标准差
     */
    private double calculateVelocityStd(Long projectId) {
        List<Sprint> recentSprints = sprintRepository.findTop5ByProjectIdAndStatusOrderByEndDateDesc(
                projectId, Sprint.SprintStatus.COMPLETED);

        if (recentSprints.size() < 2) {
            return 1.0; // 默认标准差
        }

        double[] velocities = recentSprints.stream()
                .filter(s -> s.getVelocity() != null)
                .mapToDouble(s -> s.getVelocity().doubleValue())
                .toArray();

        if (velocities.length < 2) {
            return 1.0;
        }

        double mean = java.util.Arrays.stream(velocities).average().orElse(0.0);
        double variance = java.util.Arrays.stream(velocities)
                .map(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);

        return Math.sqrt(variance);
    }

    /**
     * 映射风险等级 - 将数值概率转换为业务可理解的风险等级
     *
     * 风险等级划分标准：
     * - GREEN（绿色/低风险）：完成概率 >= 80%，Sprint 很可能按时完成
     * - YELLOW（黄色/中风险）：完成概率 50%-80%，存在一定风险，需要关注
     * - RED（红色/高风险）：完成概率 < 50%，完成风险很高，需要立即采取行动
     *
     * @param probability 模型预测的完成概率，范围 [0.0, 1.0]
     * @return String 风险等级标识：GREEN、YELLOW 或 RED
     */
    private String mapRiskLevel(double probability) {
        // 高概率完成（>=80%）：绿色等级，团队状态良好
        if (probability >= 0.8) {
            return "GREEN";
        }
        // 中等概率完成（50%-80%）：黄色等级，需要监控和调整
        else if (probability >= 0.5) {
            return "YELLOW";
        }
        // 低概率完成（<50%）：红色等级，需要紧急干预
        else {
            return "RED";
        }
    }

    /**
     * 构建特征摘要 - 提取关键特征用于前端展示和业务分析
     *
     * 从完整的19个特征中选择最重要的7个特征构建摘要对象
     * 这些特征对业务人员最有参考价值，便于理解预测结果的依据
     *
     * @param features 完整的特征向量 Map，包含19个特征
     * @return FeatureSummary 特征摘要对象，包含7个核心指标
     */
    private SprintCompletionPredictionDto.FeatureSummary buildFeatureSummary(Map<String, Double> features) {
        return SprintCompletionPredictionDto.FeatureSummary.builder()
                // 时间进度比例：已消耗时间占总时间的比例，反映 Sprint 时间进度
                // 时间进度比例：已消耗时间占总时间的比例，反映 Sprint 时间进度
                .daysElapsedRatio(features.get("elapsed_ratio"))
                // 剩余工作比例：剩余故事点占承诺故事点的比例，反映工作完成进度
                .remainingRatio(features.get("remaining_ratio"))
                // 当前开发速度：当前 Sprint 的故事点完成速度（故事点/天）
                .velocityCurrent(features.get("velocity_current"))
                // 历史平均速度：最近5个 Sprint 的平均开发速度，作为基准参考
                .velocityAvg(features.get("velocity_avg_5"))
                // 预测完成比例：按当前速度预测最终能完成的工作量占承诺的比例
                .projectedCompletionRatio(features.get("projected_completion_ratio"))
                // 阻断任务数：当前处于阻断状态的任务数量，影响进度的风险因子
                .blockedStories(features.get("blocked_stories").intValue())
                // 团队出勤率：团队成员的出勤情况，影响实际开发能力
                .attendanceRate(features.get("attendance_rate"))
                .build();  // 构建不可变的特征摘要对象
    }

    /**
     * 加载特征列配置 - 从 JSON 文件中读取模型训练时使用的特征列顺序
     *
     * 机器学习模型对特征顺序敏感，必须确保推理时的特征顺序与训练时完全一致
     * 该方法在服务启动时（@PostConstruct）执行，加载特征列配置到内存中
     *
     * 配置文件位置：src/main/resources/models/feature_columns.json
     * 文件格式：{"feature_columns": ["feature1", "feature2", ...]}
     *
     * @throws IOException 当配置文件不存在或格式错误时抛出异常
     */
    private void loadFeatureColumns() throws IOException {
        // 从 classpath 加载特征列配置文件
        ClassPathResource resource = new ClassPathResource("models/feature_columns.json");

        // 使用 try-with-resources 确保输入流正确关闭
        try (InputStream inputStream = resource.getInputStream()) {
            JsonNode jsonNode = objectMapper.readTree(inputStream);
            featureColumns = objectMapper.convertValue(
                    jsonNode.get("feature_columns"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
        }
    }
}