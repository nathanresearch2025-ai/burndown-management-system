package com.burndown.aiagent.standup.tool;

import com.burndown.entity.BurndownPoint;
import com.burndown.entity.Sprint;
import com.burndown.repository.BurndownPointRepository;
import com.burndown.repository.SprintRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Standup Agent 燃尽图工具类
 *
 * 功能说明：
 * 提供给 AI Agent 调用的燃尽图数据查询工具
 *
 * 核心功能：
 * 1. 查询 Sprint 的燃尽图数据点
 * 2. 获取最新的计划剩余工时和实际剩余工时
 * 3. 计算偏差值
 * 4. 统计任务完成情况
 *
 * 使用场景：
 * - 用户询问："当前 Sprint 的燃尽图情况如何？"
 * - 用户询问："我们的进度是否正常？"
 * - 用户询问："还剩多少工作量？"
 *
 * 工作原理：
 * AI 识别到需要燃尽图数据时，会自动调用此工具函数
 * 工具返回格式化的文本数据，AI 将其整合到自然语言回答中
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StandupBurndownTools {

    // 燃尽图数据点仓库
    private final BurndownPointRepository burndownPointRepository;

    // Sprint 仓库
    private final SprintRepository sprintRepository;

    /**
     * 获取 Sprint 的燃尽图数据
     *
     * 功能：
     * - 查询指定 Sprint 的所有燃尽图数据点
     * - 获取最新的数据点（今天或最近的一天）
     * - 计算计划剩余工时、实际剩余工时和偏差
     * - 统计任务完成情况
     *
     * 数据说明：
     * - 计划剩余工时（Ideal Remaining）：理想情况下应该剩余的工时
     * - 实际剩余工时（Actual Remaining）：实际还需要完成的工时
     * - 偏差（Deviation）：实际剩余 - 计划剩余
     *   - 正值：进度落后
     *   - 负值：进度超前
     *   - 零值：进度正常
     *
     * AI 调用时机：
     * - 用户询问 Sprint 进度
     * - 用户询问燃尽图情况
     * - 用户询问剩余工作量
     *
     * @param request 包含 Sprint ID 的请求参数
     * @return 简洁的燃尽图数据字符串，供 AI 生成自然回答
     */
    @Description("获取 Sprint 的燃尽图数据，包括计划剩余和实际剩余工时")
    //@org.springframework.ai.tool.annotation.Tool(name = "getSprintBurndown", description = "获取 Sprint 的燃尽图数据")
    public String getSprintBurndown(GetSprintBurndownRequest request) {
        log.info("Tool called: getSprintBurndown - sprintId: {}", request.sprintId());

        try {
            // 步骤1：查询 Sprint 信息
            Sprint sprint = sprintRepository.findById(request.sprintId())
                    .orElseThrow(() -> new RuntimeException("Sprint not found"));

            // 步骤2：查询燃尽图数据点，按日期升序排列
            List<BurndownPoint> points = burndownPointRepository
                    .findBySprintIdOrderByPointDateAsc(request.sprintId());

            // 步骤3：检查是否有数据
            if (points.isEmpty()) {
                return String.format("Sprint[%s]暂无数据", sprint.getName());
            }

            // 步骤4：获取最新的数据点
            BurndownPoint latestPoint = points.stream()
                    .filter(p -> !p.getPointDate().isAfter(LocalDate.now()))
                    .reduce((first, second) -> second)
                    .orElse(points.get(points.size() - 1));

            // 步骤5：提取关键数据
            BigDecimal plannedRemaining = latestPoint.getIdealRemaining();
            BigDecimal actualRemaining = latestPoint.getActualRemaining();
            BigDecimal deviation = actualRemaining.subtract(plannedRemaining);

            // 步骤6：构建简洁的数据格式
            return String.format("Sprint[%s]截至%s:计划剩余%.1f小时,实际剩余%.1f小时,偏差%.1f小时;已完成%d/%d个任务,进行中%d个",
                    sprint.getName(),
                    formatDate(latestPoint.getPointDate()),
                    plannedRemaining,
                    actualRemaining,
                    deviation,
                    latestPoint.getCompletedTasks(),
                    latestPoint.getTotalTasks(),
                    latestPoint.getInProgressTasks());

        } catch (Exception e) {
            log.error("Error getting sprint burndown: {}", e.getMessage(), e);
            return "获取燃尽图数据失败: " + e.getMessage();
        }
    }

    /**
     * 格式化日期为简洁格式
     */
    private String formatDate(LocalDate date) {
        if (date == null) return "未知";

        LocalDate today = LocalDate.now();
        if (date.equals(today)) {
            return "今天";
        } else if (date.equals(today.minusDays(1))) {
            return "昨天";
        } else {
            return date.format(java.time.format.DateTimeFormatter.ofPattern("MM月dd日"));
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
    public record GetSprintBurndownRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("Sprint ID")
            Long sprintId
    ) {}
}
