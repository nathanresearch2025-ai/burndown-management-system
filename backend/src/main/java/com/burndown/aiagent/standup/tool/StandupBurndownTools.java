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

@Slf4j
@Component
@RequiredArgsConstructor
public class StandupBurndownTools {

    private final BurndownPointRepository burndownPointRepository;
    private final SprintRepository sprintRepository;

    @Description("获取 Sprint 的燃尽图数据，包括计划剩余和实际剩余工时")
    public String getSprintBurndown(GetSprintBurndownRequest request) {
        log.info("Tool called: getSprintBurndown - sprintId: {}", request.sprintId());

        try {
            Sprint sprint = sprintRepository.findById(request.sprintId())
                    .orElseThrow(() -> new RuntimeException("Sprint not found"));

            List<BurndownPoint> points = burndownPointRepository
                    .findBySprintIdOrderByPointDateAsc(request.sprintId());

            if (points.isEmpty()) {
                return String.format("Sprint %s 暂无燃尽图数据", sprint.getName());
            }

            // Get latest point (today or most recent)
            BurndownPoint latestPoint = points.stream()
                    .filter(p -> !p.getPointDate().isAfter(LocalDate.now()))
                    .reduce((first, second) -> second)
                    .orElse(points.get(points.size() - 1));

            BigDecimal plannedRemaining = latestPoint.getIdealRemaining();
            BigDecimal actualRemaining = latestPoint.getActualRemaining();
            BigDecimal deviation = actualRemaining.subtract(plannedRemaining);

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

    public record GetSprintBurndownRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("Sprint ID")
            Long sprintId
    ) {}
}
