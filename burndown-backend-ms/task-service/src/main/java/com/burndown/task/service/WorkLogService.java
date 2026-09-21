package com.burndown.task.service;

import com.burndown.common.exception.ResourceNotFoundException;
import com.burndown.task.entity.Task;
import com.burndown.task.entity.WorkLog;
import com.burndown.task.repository.TaskRepository;
import com.burndown.task.repository.WorkLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class WorkLogService {

    private final WorkLogRepository workLogRepository;
    private final TaskRepository taskRepository;

    public Page<WorkLog> getByTaskId(Long taskId, Pageable pageable) {
        return workLogRepository.findByTaskId(taskId, pageable);
    }

    @Transactional
    public WorkLog create(Long taskId, WorkLog workLog, Long userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        workLog.setTaskId(taskId);
        workLog.setUserId(userId);
        WorkLog saved = workLogRepository.save(workLog);

        // Update task time_spent
        BigDecimal total = workLogRepository.sumTimeSpentByTaskId(taskId);
        task.setTimeSpent(total != null ? total : BigDecimal.ZERO);
        taskRepository.save(task);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        workLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkLog", id));
        workLogRepository.deleteById(id);
    }
}
