package com.burndown.task.service;

import com.burndown.common.exception.BusinessException;
import com.burndown.common.exception.ResourceNotFoundException;
import com.burndown.task.client.ProjectServiceClient;
import com.burndown.task.entity.Task;
import com.burndown.task.event.TaskEventPublisher;
import com.burndown.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskEventPublisher eventPublisher;
    private final ProjectServiceClient projectServiceClient;

    private static final List<String> VALID_STATUSES =
            List.of("TODO", "IN_PROGRESS", "IN_REVIEW", "DONE");

    @Cacheable(value = "tasks", key = "#id")
    public Task getById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }

    public Page<Task> getBySprintId(Long sprintId, Pageable pageable) {
        return taskRepository.findBySprintId(sprintId, pageable);
    }

    public Page<Task> getByProjectId(Long projectId, Pageable pageable) {
        return taskRepository.findByProjectId(projectId, pageable);
    }

    public Page<Task> getBySprintAndStatus(Long sprintId, String status, Pageable pageable) {
        return taskRepository.findBySprintIdAndStatus(sprintId, status, pageable);
    }

    @Transactional
    public Task create(Task task, Long reporterId) {
        // Validate sprint exists (with fallback)
        if (task.getSprintId() != null) {
            projectServiceClient.getSprint(task.getSprintId());
        }
        task.setReporterId(reporterId);
        if (task.getStatus() == null || !VALID_STATUSES.contains(task.getStatus())) {
            task.setStatus("TODO");
        }
        // Generate task key: PROJECT-{id} pattern (simplified)
        Task saved = taskRepository.save(task);
        if (saved.getTaskKey() == null) {
            saved.setTaskKey("TASK-" + saved.getId());
            saved = taskRepository.save(saved);
        }
        eventPublisher.publishTaskCreated(saved);
        return saved;
    }

    @Transactional
    @CacheEvict(value = "tasks", key = "#id")
    public Task updateStatus(Long id, String newStatus) {
        if (!VALID_STATUSES.contains(newStatus)) {
            throw new BusinessException("INVALID_STATUS",
                    "Invalid status: " + newStatus, HttpStatus.BAD_REQUEST);
        }
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
        String oldStatus = task.getStatus();
        task.setStatus(newStatus);
        if ("DONE".equals(newStatus) && task.getCompletedAt() == null) {
            task.setCompletedAt(LocalDateTime.now());
        }
        Task updated = taskRepository.save(task);
        eventPublisher.publishStatusChanged(updated, oldStatus);
        return updated;
    }

    @Transactional
    @CacheEvict(value = "tasks", key = "#id")
    public Task update(Long id, Task updates) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
        if (updates.getTitle() != null) task.setTitle(updates.getTitle());
        if (updates.getDescription() != null) task.setDescription(updates.getDescription());
        if (updates.getPriority() != null) task.setPriority(updates.getPriority());
        if (updates.getAssigneeId() != null) task.setAssigneeId(updates.getAssigneeId());
        if (updates.getStoryPoints() != null) task.setStoryPoints(updates.getStoryPoints());
        if (updates.getOriginalEstimate() != null) task.setOriginalEstimate(updates.getOriginalEstimate());
        if (updates.getRemainingEstimate() != null) task.setRemainingEstimate(updates.getRemainingEstimate());
        if (updates.getDueDate() != null) task.setDueDate(updates.getDueDate());
        if (updates.getSprintId() != null) task.setSprintId(updates.getSprintId());
        return taskRepository.save(task);
    }

    @Transactional
    @CacheEvict(value = "tasks", key = "#id")
    public void delete(Long id) {
        if (!taskRepository.existsById(id)) {
            throw new ResourceNotFoundException("Task", id);
        }
        taskRepository.deleteById(id);
    }

    @Transactional
    public int migrateUndoneTasks(Long fromSprintId, Long toSprintId) {
        List<Task> undone = taskRepository.findBySprintIdAndStatusNot(fromSprintId, "DONE");
        for (Task task : undone) {
            task.setOriginalSprintId(fromSprintId);
            task.setSprintId(toSprintId);
        }
        taskRepository.saveAll(undone);
        log.info("Migrated {} tasks from sprint {} to sprint {}", undone.size(), fromSprintId, toSprintId);
        return undone.size();
    }

    @Transactional
    public int compensateMigratedTasks(Long fromSprintId) {
        // find tasks that were moved OUT of fromSprintId — their originalSprintId == fromSprintId
        List<Task> migrated = taskRepository.findByOriginalSprintId(fromSprintId);
        for (Task task : migrated) {
            task.setSprintId(task.getOriginalSprintId());
            task.setOriginalSprintId(null);
        }
        taskRepository.saveAll(migrated);
        log.info("Compensated {} tasks back to sprint {}", migrated.size(), fromSprintId);
        return migrated.size();
    }

    public java.math.BigDecimal getRemainingPoints(Long sprintId) {
        java.math.BigDecimal val = taskRepository.sumRemainingPointsBySprintId(sprintId);
        return val != null ? val : java.math.BigDecimal.ZERO;
    }

    public java.math.BigDecimal getCompletedPoints(Long sprintId) {
        java.math.BigDecimal val = taskRepository.sumCompletedPointsBySprintId(sprintId);
        return val != null ? val : java.math.BigDecimal.ZERO;
    }

    public java.math.BigDecimal getTotalPoints(Long sprintId) {
        java.math.BigDecimal val = taskRepository.sumTotalPointsBySprintId(sprintId);
        return val != null ? val : java.math.BigDecimal.ZERO;
    }

    public java.util.Map<String, Long> getStatusCounts(Long sprintId) {
        java.util.Map<String, Long> counts = new java.util.HashMap<>();
        for (String status : VALID_STATUSES) {
            counts.put(status, taskRepository.countBySprintIdAndStatus(sprintId, status));
        }
        return counts;
    }
}
