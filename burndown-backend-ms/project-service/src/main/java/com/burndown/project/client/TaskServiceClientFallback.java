package com.burndown.project.client;

import com.burndown.common.dto.ApiResponse;
import com.burndown.common.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class TaskServiceClientFallback implements TaskServiceClient {

    @Override
    public ApiResponse<Integer> migrateUndoneTasks(Long sprintId, Long targetSprintId) {
        throw new BusinessException("TASK_SERVICE_UNAVAILABLE",
                "task-service is unavailable, cannot migrate tasks",
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Override
    public ApiResponse<Integer> compensateMigratedTasks(Long sprintId) {
        throw new BusinessException("TASK_SERVICE_UNAVAILABLE",
                "task-service is unavailable, cannot compensate task migration",
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
    }
}
