package com.burndown.task.controller;

import com.burndown.common.dto.ApiResponse;
import com.burndown.task.entity.WorkLog;
import com.burndown.task.service.WorkLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/worklogs")
@RequiredArgsConstructor
public class WorkLogController {

    private final WorkLogService workLogService;

    @GetMapping("/task/{taskId}")
    public ResponseEntity<ApiResponse<Page<WorkLog>>> getByTask(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                workLogService.getByTaskId(taskId, PageRequest.of(page, size))));
    }

    @PostMapping("/task/{taskId}")
    public ResponseEntity<ApiResponse<WorkLog>> createWorkLog(
            @PathVariable Long taskId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody WorkLog workLog) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(workLogService.create(taskId, workLog, userId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteWorkLog(@PathVariable Long id) {
        workLogService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "WorkLog deleted"));
    }
}
