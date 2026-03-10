package com.burndown.aiagent.standup.config;

import com.burndown.aiagent.standup.tool.StandupBurndownTools;
import com.burndown.aiagent.standup.tool.StandupRiskTools;
import com.burndown.aiagent.standup.tool.StandupTaskTools;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

/**
 * Standup Agent 配置类
 *
 * 功能说明：
 * 将工具类的方法注册为 Spring AI 可识别的 Function Bean
 * Spring AI 通过 Bean 名称来查找和调用工具函数
 *
 * 注册方式：
 * 1. 使用 @Bean 注解将方法注册为 Spring Bean
 * 2. Bean 名称必须与 .functions() 中指定的名称一致
 * 3. 使用 @Description 注解描述函数功能（AI 会根据此描述决定是否调用）
 */
@Configuration
public class StandupAgentConfig {

    /**
     * 注册 getInProgressTasks 工具函数
     *
     * 功能：获取用户当前进行中的任务列表
     * AI 调用时机：用户询问任务相关问题
     */
    @Bean
    @Description("获取用户当前进行中的任务列表")
    public Function<StandupTaskTools.GetInProgressTasksRequest, String> getInProgressTasks(
            StandupTaskTools standupTaskTools) {
        return standupTaskTools::getInProgressTasks;
    }

    /**
     * 注册 getSprintBurndown 工具函数
     *
     * 功能：获取 Sprint 的燃尽图数据
     * AI 调用时机：用户询问 Sprint 进度、燃尽图情况
     */
    @Bean
    @Description("获取 Sprint 的燃尽图数据，包括计划剩余和实际剩余工时")
    public Function<StandupBurndownTools.GetSprintBurndownRequest, String> getSprintBurndown(
            StandupBurndownTools standupBurndownTools) {
        return standupBurndownTools::getSprintBurndown;
    }

    /**
     * 注册 evaluateBurndownRisk 工具函数
     *
     * 功能：评估燃尽图偏离风险
     * AI 调用时机：用户询问风险评估、是否能按时完成
     */
    @Bean
    @Description("评估燃尽图偏离风险，返回风险等级和建议")
    public Function<StandupRiskTools.EvaluateBurndownRiskRequest, String> evaluateBurndownRisk(
            StandupRiskTools standupRiskTools) {
        return standupRiskTools::evaluateBurndownRisk;
    }
}
