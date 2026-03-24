package com.burndown.burndown.service;

import com.burndown.burndown.client.ProjectServiceClient;
import com.burndown.burndown.client.TaskServiceClient;
import com.burndown.burndown.entity.BurndownPoint;
import com.burndown.burndown.repository.BurndownPointRepository;
import com.burndown.common.dto.SprintDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BurndownService {

    private final BurndownPointRepository burndownPointRepository;
    private final TaskServiceClient taskServiceClient;
    private final ProjectServiceClient projectServiceClient;

    @Cacheable(value = "burndown", key = "#sprintId")
    public List<BurndownPoint> getBySprintId(Long sprintId) {
        return burndownPointRepository.findBySprintIdOrderByRecordDateAsc(sprintId);
    }

    @Transactional
    public BurndownPoint recordDailyPoint(Long sprintId) {
        LocalDate today = LocalDate.now();

        // Upsert: update existing or create new
        BurndownPoint point = burndownPointRepository
                .findBySprintIdAndRecordDate(sprintId, today)
                .orElse(new BurndownPoint());

        point.setSprintId(sprintId);
        point.setRecordDate(today);

        // Fetch metrics from task-service
        BigDecimal remaining = fetchSafe(() -> taskServiceClient.getRemainingPoints(sprintId).getData());
        BigDecimal completed = fetchSafe(() -> taskServiceClient.getCompletedPoints(sprintId).getData());
        BigDecimal total = fetchSafe(() -> taskServiceClient.getTotalPoints(sprintId).getData());

        point.setRemainingPoints(remaining);
        point.setCompletedPoints(completed);
        point.setTotalPoints(total);
        point.setIdealRemaining(calculateIdealRemaining(sprintId, total, today));

        return burndownPointRepository.save(point);
    }

    private BigDecimal calculateIdealRemaining(Long sprintId, BigDecimal total, LocalDate today) {
        try {
            SprintDTO sprint = projectServiceClient.getSprint(sprintId).getData();
            if (sprint == null || sprint.getStartDate() == null || sprint.getEndDate() == null) {
                return BigDecimal.ZERO;
            }
            LocalDate start = sprint.getStartDate();
            LocalDate end = sprint.getEndDate();
            long totalDays = ChronoUnit.DAYS.between(start, end);
            long elapsedDays = ChronoUnit.DAYS.between(start, today);
            if (totalDays <= 0) return BigDecimal.ZERO;
            elapsedDays = Math.min(elapsedDays, totalDays);
            BigDecimal ratio = BigDecimal.valueOf(totalDays - elapsedDays)
                    .divide(BigDecimal.valueOf(totalDays), 4, RoundingMode.HALF_UP);
            return total.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("Could not calculate ideal remaining for sprint {}: {}", sprintId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal fetchSafe(java.util.function.Supplier<BigDecimal> supplier) {
        try {
            BigDecimal val = supplier.get();
            return val != null ? val : BigDecimal.ZERO;
        } catch (Exception e) {
            log.warn("Failed to fetch metric: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }
}
