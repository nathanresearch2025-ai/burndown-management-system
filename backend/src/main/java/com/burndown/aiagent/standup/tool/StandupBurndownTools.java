package com.burndown.aiagent.standup.tool;

import com.burndown.aiagent.standup.dto.BurndownQueryResult;
import com.burndown.entity.BurndownPoint;
import com.burndown.entity.Sprint;
import com.burndown.repository.BurndownPointRepository;
import com.burndown.repository.SprintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Burndown chart tools for the Standup AI Agent.
 * Provides burndown data query and deviation analysis.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StandupBurndownTools {

    private final BurndownPointRepository burndownPointRepository;
    private final SprintRepository sprintRepository;

    /**
     * Query the burndown chart data for a specified sprint.
     *
     * @param sprintId Sprint ID
     * @return Burndown chart data query result
     */
    @Tool(description = "Query burndown chart data for a sprint, including ideal and actual burndown lines")
    public BurndownQueryResult getBurndownData(
            @ToolParam(description = "Sprint ID") Long sprintId) {

        log.info("[StandupBurndownTool] Querying burndown data for sprint: {}", sprintId);

        Optional<Sprint> sprintOpt = sprintRepository.findById(sprintId);
        if (sprintOpt.isEmpty()) {
            return BurndownQueryResult.builder()
                    .sprintId(sprintId)
                    .errorMessage("Sprint not found: " + sprintId)
                    .build();
        }

        Sprint sprint = sprintOpt.get();
        List<BurndownPoint> points = burndownPointRepository.findBySprintIdOrderByRecordDateAsc(sprintId);

        // Calculate ideal burndown line
        double totalPoints = sprint.getTotalStoryPoints() != null ? sprint.getTotalStoryPoints() : 0.0;
        long totalDays = ChronoUnit.DAYS.between(sprint.getStartDate(), sprint.getEndDate());

        // Calculate current deviation
        double deviation = 0.0;
        String deviationStatus = "ON_TRACK";

        if (!points.isEmpty()) {
            BurndownPoint latest = points.get(points.size() - 1);
            long elapsedDays = ChronoUnit.DAYS.between(sprint.getStartDate(), latest.getRecordDate());

            // Ideal remaining points at current elapsed time
            double idealRemaining = totalDays > 0
                    ? totalPoints * (1.0 - (double) elapsedDays / totalDays)
                    : 0.0;

            double actualRemaining = latest.getRemainingPoints() != null ? latest.getRemainingPoints() : 0.0;
            deviation = actualRemaining - idealRemaining;

            // Determine deviation status
            if (deviation > totalPoints * 0.2) {
                deviationStatus = "BEHIND";
            } else if (deviation < -totalPoints * 0.1) {
                deviationStatus = "AHEAD";
            }
        }

        return BurndownQueryResult.builder()
                .sprintId(sprintId)
                .sprintName(sprint.getName())
                .totalStoryPoints(totalPoints)
                .burndownPoints(points)
                .deviationFromIdeal(deviation)
                .deviationStatus(deviationStatus)
                .build();
    }

    /**
     * Calculate the burndown deviation for a specified sprint.
     *
     * @param sprintId Sprint ID
     * @param checkDate Date to check (ISO format yyyy-MM-dd); defaults to today if blank
     * @return Deviation analysis result
     */
    @Tool(description = "Calculate the burndown deviation for a sprint on a given date")
    public String calculateBurndownDeviation(
            @ToolParam(description = "Sprint ID") Long sprintId,
            @ToolParam(description = "Date to check in ISO format yyyy-MM-dd; defaults to today if blank") String checkDate) {

        log.info("[StandupBurndownTool] Calculating deviation for sprint: {}, date: {}", sprintId, checkDate);

        Optional<Sprint> sprintOpt = sprintRepository.findById(sprintId);
        if (sprintOpt.isEmpty()) {
            return "Sprint not found: " + sprintId;
        }

        Sprint sprint = sprintOpt.get();
        LocalDate targetDate = (checkDate != null && !checkDate.isBlank())
                ? LocalDate.parse(checkDate)
                : LocalDate.now();

        long totalDays = ChronoUnit.DAYS.between(sprint.getStartDate(), sprint.getEndDate());
        long elapsedDays = ChronoUnit.DAYS.between(sprint.getStartDate(), targetDate);

        double totalPoints = sprint.getTotalStoryPoints() != null ? sprint.getTotalStoryPoints() : 0.0;

        // Ideal remaining at target date
        double idealRemaining = totalDays > 0
                ? totalPoints * (1.0 - (double) elapsedDays / totalDays)
                : 0.0;

        // Find the actual burndown point closest to the target date
        List<BurndownPoint> points = burndownPointRepository.findBySprintIdOrderByRecordDateAsc(sprintId);
        Optional<BurndownPoint> closest = points.stream()
                .filter(p -> !p.getRecordDate().isAfter(targetDate))
                .reduce((first, second) -> second); // last element not after targetDate

        if (closest.isEmpty()) {
            return String.format("Sprint '%s': no burndown data found before %s. Ideal remaining: %.1f points.",
                    sprint.getName(), targetDate, idealRemaining);
        }

        double actualRemaining = closest.get().getRemainingPoints() != null
                ? closest.get().getRemainingPoints() : 0.0;
        double deviation = actualRemaining - idealRemaining;
        double deviationPct = totalPoints > 0 ? (deviation / totalPoints) * 100 : 0.0;

        String status;
        if (deviation > totalPoints * 0.2) {
            status = "BEHIND schedule (risk: high)";
        } else if (deviation > totalPoints * 0.1) {
            status = "SLIGHTLY BEHIND schedule (risk: medium)";
        } else if (deviation < -totalPoints * 0.1) {
            status = "AHEAD of schedule";
        } else {
            status = "ON TRACK";
        }

        return String.format(
                "Sprint '%s' burndown deviation as of %s: ideal remaining=%.1f pts, actual remaining=%.1f pts, " +
                        "deviation=%.1f pts (%.1f%%), status=%s",
                sprint.getName(), targetDate, idealRemaining, actualRemaining, deviation, deviationPct, status);
    }
}
