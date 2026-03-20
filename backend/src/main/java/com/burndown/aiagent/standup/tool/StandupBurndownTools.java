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
     * @return 格式化的燃尽图数据字符串，包含：
     *         - Sprint 名称
     *         - 数据日期
     *         - 计划剩余工时
     *         - 实际剩余工时
     *         - 偏差值
     *         - 任务完成统计
     */
    @Description("获取 Sprint 的燃尽图数据，包括计划剩余和实际剩余工时")
    //@org.springframework.ai.tool.annotation.Tool(name = "getSprintBurndown", description = "获取 Sprint 的燃尽图数据")
    //默认情况下，方法的名称就是工具的名称
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
                return String.format("Sprint %s 暂无燃尽图数据", sprint.getName());
            }

            // 步骤4：获取最新的数据点
            // 过滤出不晚于今天的数据点，取最后一个（最新的）
            // 如果所有数据点都在未来，则取第一个数据点
            BurndownPoint latestPoint = points.stream()
                    .filter(p -> !p.getPointDate().isAfter(LocalDate.now()))
                    .reduce((first, second) -> second)  // 取最后一个
                    .orElse(points.get(points.size() - 1));

            // 步骤5：提取关键数据
            BigDecimal plannedRemaining = latestPoint.getIdealRemaining();  // 计划剩余工时
            BigDecimal actualRemaining = latestPoint.getActualRemaining();  // 实际剩余工时
            BigDecimal deviation = actualRemaining.subtract(plannedRemaining);  // 偏差

            // 步骤6：构建格式化的返回结果
            StringBuilder result = new StringBuilder();
            result.append(String.format("Sprint: %s\n", sprint.getName()));
            result.append(String.format("日期: %s\n", latestPoint.getPointDate()));
            result.append(String.format("计划剩余工时: %.1f 小时\n", plannedRemaining));
            result.append(String.format("实际剩余工时: %.1f 小时\n", actualRemaining));
            result.append(String.format("偏差: %.1f 小时\n", deviation));
            result.append(String.format("已完成任务: %d/%d\n",
                    latestPoint.getCompletedTasks(), latestPoint.getTotalTasks()));
            result.append(String.format("进行中任务: %d\n", latestPoint.getInProgressTasks()));

            return result.toString();

        } catch (Exception e) {
            log.error("Error getting sprint burndown: {}", e.getMessage(), e);
            return "获取燃尽图数据失败: " + e.getMessage();
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
