package com.burndown.aiagent.standup.config;

import com.burndown.aiagent.standup.tool.StandupBurndownTools;
import com.burndown.aiagent.standup.tool.StandupRiskTools;
import com.burndown.aiagent.standup.tool.StandupTaskTools;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

/**
 * Configuration class for the Standup Agent.
 *
 * Purpose:
 * Registers tool methods as Spring AI-recognized Function Beans.
 * Spring AI looks up and invokes tool functions by Bean name.
 *
 * Registration rules:
 * 1. Annotate each method with @Bean to register it as a Spring Bean.
 * 2. The Bean name must match the name specified in .functions().
 * 3. Use the @Description annotation to describe what the function does
 *    (the AI uses this description to decide whether to call it).
 */
@Configuration
public class StandupAgentConfig {

    /**
     * Register the getInProgressTasks tool function.
     *
     * Purpose: retrieve the list of tasks currently in progress for the user.
     * When the AI calls it: when the user asks about task-related questions.
     */
    @Bean
    @Description("Get the list of tasks currently in progress for the user")
    public Function<StandupTaskTools.GetInProgressTasksRequest, String> getInProgressTasks(
            StandupTaskTools standupTaskTools) {
        return standupTaskTools::getInProgressTasks;
    }

    /**
     * Register the getSprintBurndown tool function.
     *
     * Purpose: retrieve burndown chart data for a Sprint.
     * When the AI calls it: when the user asks about Sprint progress or burndown status.
     */
    @Bean
    @Description("Get the burndown chart data for a Sprint, including planned and actual remaining hours")
    public Function<StandupBurndownTools.GetSprintBurndownRequest, String> getSprintBurndown(
            StandupBurndownTools standupBurndownTools) {
        return standupBurndownTools::getSprintBurndown;
    }

    /**
     * Register the evaluateBurndownRisk tool function.
     *
     * Purpose: assess the risk of burndown chart deviation.
     * When the AI calls it: when the user asks about risk assessment or on-time delivery.
     */
    @Bean
    @Description("Evaluate the burndown deviation risk and return a risk level with recommendations")
    public Function<StandupRiskTools.EvaluateBurndownRiskRequest, String> evaluateBurndownRisk(
            StandupRiskTools standupRiskTools) {
        return standupRiskTools::evaluateBurndownRisk;
    }
}
