package com.burndown.task.controller;

import com.burndown.common.dto.ApiResponse;
import com.burndown.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/tasks")
@RequiredArgsConstructor
public class TaskInternalController {

    private final TaskService taskService;

    @PostMapping("/sprint/{sprintId}/migrate")
    public ResponseEntity<ApiResponse<Integer>> migrateUndoneTasks(
            @PathVariable Long sprintId,
            @RequestParam Long targetSprintId) {
        int count = taskService.migrateUndoneTasks(sprintId, targetSprintId);
        return ResponseEntity.ok(ApiResponse.ok(count, "Migrated " + count + " tasks"));
    }

    @PostMapping("/sprint/{sprintId}/compensate")
    public ResponseEntity<ApiResponse<Integer>> compensateMigratedTasks(
            @PathVariable Long sprintId) {
        int count = taskService.compensateMigratedTasks(sprintId);
        return ResponseEntity.ok(ApiResponse.ok(count, "Compensated " + count + " tasks"));
    }
}
