package com.burndown.aiagent.standup.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Standup Agent 风险评估工具类
 *
 * 功能说明：
 * 提供给 AI Agent 调用的燃尽图风险评估工具
 *
 * 核心功能：
 * 1. 计算实际剩余工时与计划剩余工时的偏差比例
 * 2. 根据偏差比例评估风险等级（LOW/MEDIUM/HIGH）
 * 3. 提供针对性的改进建议
 *
 * 风险等级判定标准：
 * - LOW（低风险）：偏差比例 ≤ 5%
 *   - 进度良好，按计划推进
 *   - 建议：继续保持当前节奏
 *
 * - MEDIUM（中等风险）：5% < 偏差比例 ≤ 20%
 *   - 存在一定延期风险
 *   - 建议：优先推进高优任务，减少并行工作
 *
 * - HIGH（高风险）：偏差比例 > 20%
 *   - 存在严重延期风险
 *   - 建议：立即召开团队会议，重新评估任务优先级和资源分配
 *
 * 使用场景：
 * - 用户询问："有延期风险吗？"
 * - 用户询问："我们能按时完成吗？"
 * - 用户询问："当前有什么风险？"
 *
 * 工作原理：
 * AI 在获取燃尽图数据后，会自动调用此工具进行风险评估
 * 工具返回风险等级和建议，AI 将其整合到自然语言回答中
 */
@Slf4j
@Component
public class StandupRiskTools {

    /**
     * 评估燃尽图偏离风险
     *
     * 功能：
     * - 计算实际剩余工时与计划剩余工时的偏差
     * - 计算偏差比例（偏差 / 计划剩余）
     * - 根据偏差比例判定风险等级
     * - 提供针对性的改进建议
     *
     * 计算公式：
     * - 偏差 = 实际剩余工时 - 计划剩余工时
     * - 偏差比例 = 偏差 / 计划剩余工时
     *
     * 风险判定逻辑：
     * - 偏差比例 ≤ 5%：LOW（低风险）
     * - 5% < 偏差比例 ≤ 20%：MEDIUM（中等风险）
     * - 偏差比例 > 20%：HIGH（高风险）
     *
     * AI 调用时机：
     * - 用户询问风险评估
     * - 用户询问是否能按时完成
     * - AI 在分析燃尽图后自动调用
     *
     * @param request 包含计划剩余工时和实际剩余工时的请求参数
     * @return 格式化的风险评估结果字符串，包含：
     *         - 风险等级（LOW/MEDIUM/HIGH）
     *         - 偏差比例（百分比）
     *         - 偏差工时（小时）
     *         - 改进建议
     */
    @Description("评估燃尽图偏离风险，返回风险等级和建议")
    public String evaluateBurndownRisk(EvaluateBurndownRiskRequest request) {
        log.info("Tool called: evaluateBurndownRisk - planned: {}, actual: {}",
                request.plannedRemaining(), request.actualRemaining());

        try {
            BigDecimal planned = request.plannedRemaining();
            BigDecimal actual = request.actualRemaining();

            // 步骤1：检查计划剩余工时是否为 0
            if (planned.compareTo(BigDecimal.ZERO) == 0) {
                return "计划剩余工时为 0，无法评估风险";
            }

            // 步骤2：计算偏差和偏差比例
            // 偏差 = 实际剩余 - 计划剩余
            BigDecimal deviation = actual.subtract(planned);
            // 偏差比例 = 偏差 / 计划剩余（保留4位小数）
            BigDecimal ratio = deviation.divide(planned, 4, RoundingMode.HALF_UP);

            // 步骤3：根据偏差比例判定风险等级和建议
            String riskLevel;
            String suggestion;

            if (ratio.compareTo(new BigDecimal("0.05")) <= 0) {
                // 偏差比例 ≤ 5%：低风险
                riskLevel = "LOW";
                suggestion = "进度良好，继续保持当前节奏";
            } else if (ratio.compareTo(new BigDecimal("0.20")) <= 0) {
                // 5% < 偏差比例 ≤ 20%：中等风险
                riskLevel = "MEDIUM";
                suggestion = "存在中等延期风险，建议优先推进高优任务并减少并行工作";
            } else {
                // 偏差比例 > 20%：高风险
                riskLevel = "HIGH";
                suggestion = "存在高延期风险，建议立即召开团队会议，重新评估任务优先级和资源分配";
            }

            // 步骤4：构建格式化的返回结果
            StringBuilder result = new StringBuilder();
            result.append(String.format("风险等级: %s\n", riskLevel));
            result.append(String.format("偏差比例: %.1f%%\n", ratio.multiply(new BigDecimal("100"))));
            result.append(String.format("偏差工时: %.1f 小时\n", deviation));
            result.append(String.format("建议: %s\n", suggestion));

            return result.toString();

        } catch (Exception e) {
            log.error("Error evaluating burndown risk: {}", e.getMessage(), e);
            return "风险评估失败: " + e.getMessage();
        }
    }

    /**
     * 工具函数的请求参数定义
     *
     * 使用 Java Record 类型（不可变数据类）
     * @JsonProperty 和 @JsonPropertyDescription 注解用于：
     * - 告诉 AI 这个参数的含义
     * - 让 AI 知道如何构造调用参数
     */
    public record EvaluateBurndownRiskRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("计划剩余工时")
            BigDecimal plannedRemaining,

            @JsonProperty(required = true)
            @JsonPropertyDescription("实际剩余工时")
            BigDecimal actualRemaining
    ) {}
}
