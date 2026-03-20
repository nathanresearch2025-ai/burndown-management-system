package com.burndown.aiagent.standup.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Standup Agent risk assessment tool.
 *
 * Provides a burndown risk assessment tool callable by the AI Agent.
 *
 * Core functionality:
 * 1. Calculate the deviation ratio between actual and planned remaining hours.
 * 2. Assess the risk level (LOW/MEDIUM/HIGH) based on the deviation ratio.
 * 3. Provide targeted improvement recommendations.
 *
 * Risk level criteria:
 * - LOW (low risk): deviation ratio <= 5%
 *   - Progress is on track.
 *   - Recommendation: maintain the current pace.
 *
 * - MEDIUM (medium risk): 5% < deviation ratio <= 20%
 *   - Some delay risk exists.
 *   - Recommendation: prioritize high-priority tasks and reduce parallel work.
 *
 * - HIGH (high risk): deviation ratio > 20%
 *   - Serious delay risk exists.
 *   - Recommendation: hold a team meeting immediately and re-evaluate task priorities and resource allocation.
 *
 * Usage scenarios:
 * - User asks: "Is there a risk of delay?"
 * - User asks: "Can we finish on time?"
 * - User asks: "What risks do we currently have?"
 *
 * How it works:
 * After obtaining burndown data, the AI automatically calls this tool for risk assessment.
 * The tool returns the risk level and recommendations; the AI integrates these into its natural-language answer.
 */
@Slf4j
@Component
public class StandupRiskTools {

    /**
     * Assess the burndown deviation risk.
     *
     * - Calculates the deviation between actual and planned remaining hours.
     * - Calculates the deviation ratio (deviation / planned remaining).
     * - Determines the risk level based on the deviation ratio.
     * - Provides targeted improvement recommendations.
     *
     * Formulas:
     * - deviation = actual remaining hours - planned remaining hours
     * - deviation ratio = deviation / planned remaining hours
     *
     * Risk determination logic:
     * - deviation ratio <= 5%: LOW
     * - 5% < deviation ratio <= 20%: MEDIUM
     * - deviation ratio > 20%: HIGH
     *
     * AI invocation triggers:
     * - User asks for risk assessment.
     * - User asks whether the Sprint can be completed on time.
     * - AI automatically calls this after analyzing burndown data.
     *
     * @param request request containing planned and actual remaining hours
     * @return formatted risk assessment string containing:
     *         - risk level (LOW/MEDIUM/HIGH)
     *         - deviation ratio (percentage)
     *         - deviation in hours
     *         - improvement recommendation
     */
    @Description("Assess burndown deviation risk and return risk level with recommendations")
    public String evaluateBurndownRisk(EvaluateBurndownRiskRequest request) {
        log.info("Tool called: evaluateBurndownRisk - planned: {}, actual: {}",
                request.plannedRemaining(), request.actualRemaining());

        try {
            BigDecimal planned = request.plannedRemaining();
            BigDecimal actual = request.actualRemaining();

            // Step 1: Check if planned remaining hours is 0.
            if (planned.compareTo(BigDecimal.ZERO) == 0) {
                return "Planned remaining hours is 0, unable to assess risk";
            }

            // Step 2: Calculate deviation and deviation ratio.
            // deviation = actual remaining - planned remaining
            BigDecimal deviation = actual.subtract(planned);
            // deviation ratio = deviation / planned remaining (4 decimal places)
            BigDecimal ratio = deviation.divide(planned, 4, RoundingMode.HALF_UP);

            // Step 3: Determine risk level and recommendation based on deviation ratio.
            String riskLevel;
            String suggestion;

            if (ratio.compareTo(new BigDecimal("0.05")) <= 0) {
                // deviation ratio <= 5%: low risk
                riskLevel = "LOW";
                suggestion = "Progress is on track, maintain the current pace";
            } else if (ratio.compareTo(new BigDecimal("0.20")) <= 0) {
                // 5% < deviation ratio <= 20%: medium risk
                riskLevel = "MEDIUM";
                suggestion = "Medium delay risk detected; prioritize high-priority tasks and reduce parallel work";
            } else {
                // deviation ratio > 20%: high risk
                riskLevel = "HIGH";
                suggestion = "High delay risk detected; hold a team meeting immediately and re-evaluate task priorities and resource allocation";
            }

            // Step 4: Build the formatted return value.
            StringBuilder result = new StringBuilder();
            result.append(String.format("Risk level: %s\n", riskLevel));
            result.append(String.format("Deviation ratio: %.1f%%\n", ratio.multiply(new BigDecimal("100"))));
            result.append(String.format("Deviation hours: %.1f hours\n", deviation));
            result.append(String.format("Recommendation: %s\n", suggestion));

            return result.toString();

        } catch (Exception e) {
            log.error("Error evaluating burndown risk: {}", e.getMessage(), e);
            return "Risk assessment failed: " + e.getMessage();
        }
    }

    /**
     * Request parameter definition for the tool function.
     *
     * Uses a Java Record type (immutable data class).
     * @JsonProperty and @JsonPropertyDescription annotations are used to:
     * - Tell the AI what each parameter means.
     * - Let the AI know how to construct the call arguments.
     */
    public record EvaluateBurndownRiskRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("Planned remaining hours")
            BigDecimal plannedRemaining,

            @JsonProperty(required = true)
            @JsonPropertyDescription("Actual remaining hours")
            BigDecimal actualRemaining
    ) {}
}
