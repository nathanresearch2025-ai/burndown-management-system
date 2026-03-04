package com.burndown.service;

import com.burndown.entity.BurndownPoint;
import com.burndown.entity.Sprint;
import com.burndown.entity.Task;
import com.burndown.entity.WorkLog;
import com.burndown.repository.BurndownPointRepository;
import com.burndown.repository.SprintRepository;
import com.burndown.repository.TaskRepository;
import com.burndown.repository.WorkLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class BurndownService {

    private final BurndownPointRepository burndownPointRepository;
    private final SprintRepository sprintRepository;
    private final TaskRepository taskRepository;
    private final WorkLogRepository workLogRepository;

    public BurndownService(BurndownPointRepository burndownPointRepository,
                          SprintRepository sprintRepository,
                          TaskRepository taskRepository,
                          WorkLogRepository workLogRepository) {
        this.burndownPointRepository = burndownPointRepository;
        this.sprintRepository = sprintRepository;
        this.taskRepository = taskRepository;
        this.workLogRepository = workLogRepository;
    }

    @Transactional
    public void calculateBurndown(Long sprintId) {
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new RuntimeException("Sprint not found"));

        // Delete existing burndown data for this sprint to avoid duplicate key violations
        burndownPointRepository.deleteBySprintId(sprintId);
        burndownPointRepository.flush();

        List<Task> tasks = taskRepository.findBySprintId(sprintId);

        BigDecimal totalEstimate = tasks.stream()
                .map(task -> task.getOriginalEstimate() != null ? task.getOriginalEstimate() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long totalDays = ChronoUnit.DAYS.between(sprint.getStartDate(), sprint.getEndDate()) + 1;

        for (long i = 0; i < totalDays; i++) {
            LocalDate currentDate = sprint.getStartDate().plusDays(i);

            BigDecimal idealRemaining = totalEstimate
                    .multiply(BigDecimal.valueOf(totalDays - i))
                    .divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_UP);

            // Calculate actual remaining for this specific date
            BigDecimal actualRemaining = calculateActualRemainingForDate(tasks, currentDate);

            long completedTasks = countCompletedTasksByDate(tasks, currentDate);
            long inProgressTasks = countInProgressTasksByDate(tasks, currentDate);

            BurndownPoint point = burndownPointRepository
                    .findBySprintIdAndPointDate(sprintId, currentDate)
                    .orElse(new BurndownPoint());

            point.setSprintId(sprintId);
            point.setPointDate(currentDate);
            point.setIdealRemaining(idealRemaining);
            point.setActualRemaining(actualRemaining);
            point.setTotalTasks(tasks.size());
            point.setCompletedTasks((int) completedTasks);
            point.setInProgressTasks((int) inProgressTasks);

            burndownPointRepository.save(point);
        }
    }

    private BigDecimal calculateActualRemainingForDate(List<Task> tasks, LocalDate date) {
        BigDecimal totalRemaining = BigDecimal.ZERO;

        for (Task task : tasks) {
            BigDecimal taskRemaining = getTaskRemainingEstimateForDate(task, date);
            totalRemaining = totalRemaining.add(taskRemaining);
        }

        return totalRemaining;
    }

    private BigDecimal getTaskRemainingEstimateForDate(Task task, LocalDate date) {
        // Find the latest work log for this task on or before the given date
        List<WorkLog> workLogs = workLogRepository.findLatestByTaskIdBeforeDate(task.getId(), date);

        if (!workLogs.isEmpty()) {
            // Use the remaining estimate from the latest work log
            WorkLog latestLog = workLogs.get(0);
            return latestLog.getRemainingEstimate() != null
                ? latestLog.getRemainingEstimate()
                : BigDecimal.ZERO;
        }

        // If no work log exists before this date, use the original estimate
        return task.getOriginalEstimate() != null
            ? task.getOriginalEstimate()
            : BigDecimal.ZERO;
    }

    private long countCompletedTasksByDate(List<Task> tasks, LocalDate date) {
        return tasks.stream()
                .filter(task -> task.getStatus() == Task.TaskStatus.DONE
                    && task.getResolvedAt() != null
                    && !task.getResolvedAt().toLocalDate().isAfter(date))
                .count();
    }

    private long countInProgressTasksByDate(List<Task> tasks, LocalDate date) {
        // For in-progress tasks, we check if there's any work log on or before this date
        return tasks.stream()
                .filter(task -> {
                    if (task.getStatus() == Task.TaskStatus.DONE
                        && task.getResolvedAt() != null
                        && !task.getResolvedAt().toLocalDate().isAfter(date)) {
                        return false; // Already completed by this date
                    }

                    // Check if there's any work log on or before this date
                    List<WorkLog> logs = workLogRepository.findLatestByTaskIdBeforeDate(task.getId(), date);
                    return !logs.isEmpty();
                })
                .count();
    }

    public List<BurndownPoint> getBurndownData(Long sprintId) {
        return burndownPointRepository.findBySprintIdOrderByPointDateAsc(sprintId);
    }
}
