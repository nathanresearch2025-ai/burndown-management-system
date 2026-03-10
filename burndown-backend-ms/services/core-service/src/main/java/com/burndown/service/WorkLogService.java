package com.burndown.service;

import com.burndown.dto.LogWorkRequest;
import com.burndown.entity.Task;
import com.burndown.entity.WorkLog;
import com.burndown.repository.TaskRepository;
import com.burndown.repository.WorkLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class WorkLogService {

    private final WorkLogRepository workLogRepository;
    private final TaskRepository taskRepository;

    public WorkLogService(WorkLogRepository workLogRepository, TaskRepository taskRepository) {
        this.workLogRepository = workLogRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional
    public WorkLog logWork(LogWorkRequest request, Long userId) {
        Task task = taskRepository.findById(request.getTaskId())
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (workLogRepository.findByTaskIdAndUserIdAndWorkDate(
                request.getTaskId(), userId, request.getWorkDate()).isPresent()) {
            throw new RuntimeException("Work log already exists for this date");
        }

        // 计算新的已用工时
        BigDecimal currentTimeSpent = task.getTimeSpent() != null ? task.getTimeSpent() : BigDecimal.ZERO;
        BigDecimal newTimeSpent = currentTimeSpent.add(request.getTimeSpent());

        // 基于预估工时自动计算剩余工时
        BigDecimal originalEstimate = task.getOriginalEstimate() != null ? task.getOriginalEstimate() : BigDecimal.ZERO;
        BigDecimal calculatedRemaining = originalEstimate.subtract(newTimeSpent);

        // 如果用户提供了剩余工时，使用用户提供的值；否则使用计算值
        BigDecimal finalRemaining = request.getRemainingEstimate() != null
            ? request.getRemainingEstimate()
            : calculatedRemaining;

        WorkLog workLog = new WorkLog();
        workLog.setTaskId(request.getTaskId());
        workLog.setUserId(userId);
        workLog.setWorkDate(request.getWorkDate());
        workLog.setTimeSpent(request.getTimeSpent());
        workLog.setRemainingEstimate(finalRemaining);
        workLog.setComment(request.getComment());

        workLog = workLogRepository.save(workLog);

        // 更新任务的工时信息
        task.setTimeSpent(newTimeSpent);
        task.setRemainingEstimate(finalRemaining);
        taskRepository.save(task);

        return workLog;
    }

    public List<WorkLog> getWorkLogsByTask(Long taskId) {
        return workLogRepository.findByTaskId(taskId);
    }

    public List<WorkLog> getWorkLogsByUser(Long userId) {
        return workLogRepository.findByUserId(userId);
    }
}
