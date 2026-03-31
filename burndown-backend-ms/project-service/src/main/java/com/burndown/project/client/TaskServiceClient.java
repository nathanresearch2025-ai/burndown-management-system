package com.burndown.project.client;

import com.burndown.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "task-service", fallback = TaskServiceClientFallback.class)
public interface TaskServiceClient {

    @PostMapping("/api/v1/internal/tasks/sprint/{sprintId}/migrate")
    ApiResponse<Integer> migrateUndoneTasks(
            @PathVariable Long sprintId,
            @RequestParam Long targetSprintId);

    @PostMapping("/api/v1/internal/tasks/sprint/{sprintId}/compensate")
    ApiResponse<Integer> compensateMigratedTasks(@PathVariable Long sprintId);
}
