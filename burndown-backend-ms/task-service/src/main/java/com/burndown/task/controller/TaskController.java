package com.burndown.task.controller;

import com.burndown.common.dto.ApiResponse;
import com.burndown.task.entity.Task;
import com.burndown.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Task>> getTask(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.getById(id)));
    }

    @GetMapping("/sprint/{sprintId}")
    public ResponseEntity<ApiResponse<Page<Task>>> getBySprintId(
            @PathVariable Long sprintId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        size = Math.min(size, 50);
        Page<Task> tasks = (status != null)
                ? taskService.getBySprintAndStatus(sprintId, status, PageRequest.of(page, size))
                : taskService.getBySprintId(sprintId, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(tasks));
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<ApiResponse<Page<Task>>> getByProjectId(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                taskService.getByProjectId(projectId, PageRequest.of(page, Math.min(size, 50)))));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Task>> createTask(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody Task task) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(taskService.create(task, userId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Task>> updateTask(
            @PathVariable Long id,
            @RequestBody Task updates) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.update(id, updates)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Task>> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String newStatus = body.get("status");
        return ResponseEntity.ok(ApiResponse.ok(taskService.updateStatus(id, newStatus)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(@PathVariable Long id) {
        taskService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Task deleted"));
    }

    @GetMapping("/sprint/{sprintId}/remaining-points")
    public ResponseEntity<ApiResponse<java.math.BigDecimal>> getRemainingPoints(@PathVariable Long sprintId) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.getRemainingPoints(sprintId)));
    }

    @GetMapping("/sprint/{sprintId}/completed-points")
    public ResponseEntity<ApiResponse<java.math.BigDecimal>> getCompletedPoints(@PathVariable Long sprintId) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.getCompletedPoints(sprintId)));
    }

    @GetMapping("/sprint/{sprintId}/total-points")
    public ResponseEntity<ApiResponse<java.math.BigDecimal>> getTotalPoints(@PathVariable Long sprintId) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.getTotalPoints(sprintId)));
    }

    @GetMapping("/sprint/{sprintId}/status-counts")
    public ResponseEntity<ApiResponse<java.util.Map<String, Long>>> getStatusCounts(@PathVariable Long sprintId) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.getStatusCounts(sprintId)));
    }
}
