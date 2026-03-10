package com.burndown.aiagent.standup.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Component
public class StandupRiskTools {

    @Description("评估燃尽图偏离风险，返回风险等级和建议")
    public String evaluateBurndownRisk(EvaluateBurndownRiskRequest request) {
        log.info("Tool called: evaluateBurndownRisk - planned: {}, actual: {}",
                request.plannedRemaining(), request.actualRemaining());

        try {
            BigDecimal planned = request.plannedRemaining();
            BigDecimal actual = request.actualRemaining();

            if (planned.compareTo(BigDecimal.ZERO) == 0) {
                return "计划剩余工时为 0，无法评估风险";
            }

            // Calculate deviation ratio
            BigDecimal deviation = actual.subtract(planned);
            BigDecimal ratio = deviation.divide(planned, 4, RoundingMode.HALF_UP);

            String riskLevel;
            String suggestion;

            if (ratio.compareTo(new BigDecimal("0.05")) <= 0) {
                riskLevel = "LOW";
                suggestion = "进度良好，继续保持当前节奏";
            } else if (ratio.compareTo(new BigDecimal("0.20")) <= 0) {
                riskLevel = "MEDIUM";
                suggestion = "存在中等延期风险，建议优先推进高优任务并减少并行工作";
            } else {
                riskLevel = "HIGH";
                suggestion = "存在高延期风险，建议立即召开团队会议，重新评估任务优先级和资源分配";
            }

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

    public record EvaluateBurndownRiskRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("计划剩余工时")
            BigDecimal plannedRemaining,

            @JsonProperty(required = true)
            @JsonPropertyDescription("实际剩余工时")
            BigDecimal actualRemaining
    ) {}
}
