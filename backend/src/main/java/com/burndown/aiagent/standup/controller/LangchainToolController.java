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
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/agent/tools")
@RequiredArgsConstructor
@Tag(name = "LangChain Tools", description = "工具型接口供 LangChain 使用")
public class LangchainToolController {

    private final StandupTaskTools standupTaskTools;
    private final StandupBurndownTools standupBurndownTools;
    private final StandupRiskTools standupRiskTools;
    private final BurndownPointRepository burndownPointRepository;

    @RequestMapping(value = "/in-progress-tasks", method = {RequestMethod.GET, RequestMethod.POST})
    @Operation(summary = "获取进行中任务列表")
    public String inProgressTasks(@RequestBody(required = false) LangchainToolRequest request,
                                   @RequestParam(required = false) Long projectId,
                                   @RequestParam(required = false) Long userId) {
        log.info("=== [LangchainToolController] /in-progress-tasks called ===");
        log.info("Request body: {}", request);
        log.info("Query params - projectId: {}, userId: {}", projectId, userId);

        // 支持 POST (body) 和 GET (query params)
        Long finalProjectId = request != null ? request.getProjectId() : projectId;
        Long finalUserId = request != null ? request.getUserId() : userId;

        log.info("Final params - projectId: {}, userId: {}", finalProjectId, finalUserId);

        String result = standupTaskTools.getInProgressTasks(
                new StandupTaskTools.GetInProgressTasksRequest(finalProjectId, finalUserId)
        );

        log.info("Response: {}", result.length() > 200 ? result.substring(0, 200) + "..." : result);
        log.info("=== [LangchainToolController] /in-progress-tasks completed ===\n");

        return result;
    }

    @RequestMapping(value = "/sprint-burndown", method = {RequestMethod.GET, RequestMethod.POST})
    @Operation(summary = "获取 Sprint 燃尽图数据")
    public String sprintBurndown(@RequestBody(required = false) LangchainToolRequest request,
                                 @RequestParam(required = false) Long sprintId) {
        log.info("=== [LangchainToolController] /sprint-burndown called ===");
        log.info("Request body: ", request);
        log.info("Query params - sprintId: {}", sprintId);

        Long finalSprintId = request != null ? request.getSprintId() : sprintId;
        log.info("Final params - sprintId: {}", finalSprintId);

        String result = standupBurndownTools.getSprintBurndown(
                new StandupBurndownTools.GetSprintBurndownRequest(finalSprintId)
        );

        log.info("Response: {}", result.length() > 200 ? result.substring(0, 200) + "..." : result);
        log.info("=== [LangchainToolController] /sprint-burndown completed ===\n");

        return result;
    }

    @RequestMapping(value = "/burndown-risk", method = {RequestMethod.GET, RequestMethod.POST})
    @Operation(summary = "评估燃尽图风险")
    public String burndownRisk(@RequestBody(required = false) LangchainToolRequest request,
                               @RequestParam(required = false) Long sprintId) {
        log.info("=== [LangchainToolController] /burndown-risk called ===");
        log.info("Request body: {}", request);
        log.info("Query params - sprintId: {}", sprintId);

        Long finalSprintId = request != null ? request.getSprintId() : sprintId;
        log.info("Final params - sprintId: {}", finalSprintId);

        BurndownPoint latestPoint = getLatestBurndownPoint(finalSprintId);
        if (latestPoint == null) {
            log.warn("No burndown data found for sprintId: {}", finalSprintId);
            return "暂无燃尽图数据，无法评估风险";
        }

        BigDecimal planned = latestPoint.getIdealRemaining();
        BigDecimal actual = latestPoint.getActualRemaining();
        log.info("Burndown data - planned: {}, actual: {}", planned, actual);

        String result = standupRiskTools.evaluateBurndownRisk(
                new StandupRiskTools.EvaluateBurndownRiskRequest(planned, actual)
        );

        log.info("Response: {}", result);
        log.info("=== [LangchainToolController] /burndown-risk completed ===\n");

        return result;
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
