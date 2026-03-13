package com.burndown.aiagent.standup.controller;

import com.burndown.aiagent.standup.dto.LangchainToolRequest;
import com.burndown.aiagent.standup.tool.StandupBurndownTools;
import com.burndown.aiagent.standup.tool.StandupRiskTools;
import com.burndown.aiagent.standup.tool.StandupTaskTools;
import com.burndown.entity.BurndownPoint;
import com.burndown.repository.BurndownPointRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/agent/tools")
@RequiredArgsConstructor
@Tag(name = "LangChain Tools", description = "工具型接口供 LangChain 使用")
public class LangchainToolController {

    private final StandupTaskTools standupTaskTools;
    private final StandupBurndownTools standupBurndownTools;
    private final StandupRiskTools standupRiskTools;
    private final BurndownPointRepository burndownPointRepository;

    @PostMapping("/in-progress-tasks")
    @Operation(summary = "获取进行中任务列表")
    public String inProgressTasks(@RequestBody LangchainToolRequest request) {
        return standupTaskTools.getInProgressTasks(
                new StandupTaskTools.GetInProgressTasksRequest(request.getProjectId(), request.getUserId())
        );
    }

    @PostMapping("/sprint-burndown")
    @Operation(summary = "获取 Sprint 燃尽图数据")
    public String sprintBurndown(@RequestBody LangchainToolRequest request) {
        return standupBurndownTools.getSprintBurndown(
                new StandupBurndownTools.GetSprintBurndownRequest(request.getSprintId())
        );
    }

    @PostMapping("/burndown-risk")
    @Operation(summary = "评估燃尽图风险")
    public String burndownRisk(@RequestBody LangchainToolRequest request) {
        BurndownPoint latestPoint = getLatestBurndownPoint(request.getSprintId());
        if (latestPoint == null) {
            return "暂无燃尽图数据，无法评估风险";
        }

        BigDecimal planned = latestPoint.getIdealRemaining();
        BigDecimal actual = latestPoint.getActualRemaining();

        return standupRiskTools.evaluateBurndownRisk(
                new StandupRiskTools.EvaluateBurndownRiskRequest(planned, actual)
        );
    }

    private BurndownPoint getLatestBurndownPoint(Long sprintId) {
        if (sprintId == null) {
            return null;
        }
        List<BurndownPoint> points = burndownPointRepository.findBySprintIdOrderByPointDateAsc(sprintId);
        if (points.isEmpty()) {
            return null;
        }
        return points.stream()
                .filter(point -> !point.getPointDate().isAfter(LocalDate.now()))
                .reduce((first, second) -> second)
                .orElse(points.get(points.size() - 1));
    }
}
