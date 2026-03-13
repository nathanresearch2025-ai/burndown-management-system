package com.burndown.controller;

import com.burndown.config.JwtTokenProvider;
import com.burndown.dto.LogWorkRequest;
import com.burndown.entity.WorkLog;
import com.burndown.service.WorkLogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/worklogs")
public class WorkLogController {

    private final WorkLogService workLogService;

    public WorkLogController(WorkLogService workLogService) {
        this.workLogService = workLogService;
    }

    @PostMapping
    public ResponseEntity<WorkLog> logWork(@Valid @RequestBody LogWorkRequest request,
                                           Authentication authentication) {
        JwtTokenProvider.UserPrincipal principal = (JwtTokenProvider.UserPrincipal) authentication.getPrincipal();
        Long userId = principal.getId();
        WorkLog workLog = workLogService.logWork(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(workLog);
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity<List<WorkLog>> getWorkLogsByTask(@PathVariable Long taskId) {
        List<WorkLog> workLogs = workLogService.getWorkLogsByTask(taskId);
        return ResponseEntity.ok(workLogs);
    }

    @GetMapping("/my-worklogs")
    public ResponseEntity<List<WorkLog>> getMyWorkLogs(Authentication authentication) {
        JwtTokenProvider.UserPrincipal principal = (JwtTokenProvider.UserPrincipal) authentication.getPrincipal();
        Long userId = principal.getId();
        List<WorkLog> workLogs = workLogService.getWorkLogsByUser(userId);
        return ResponseEntity.ok(workLogs);
    }
}
