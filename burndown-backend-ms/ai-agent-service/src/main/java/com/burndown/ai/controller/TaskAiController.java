package com.burndown.ai.controller;

import com.burndown.ai.service.TaskAiService;
import com.burndown.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai/tasks")
@RequiredArgsConstructor
public class TaskAiController {

    private final TaskAiService taskAiService;

    @PostMapping("/generate-description")
    public ResponseEntity<ApiResponse<String>> generateDescription(
            @RequestBody Map<String, Object> request) {
        Long projectId = request.get("projectId") != null
                ? Long.valueOf(request.get("projectId").toString()) : null;
        Long sprintId = request.get("sprintId") != null
                ? Long.valueOf(request.get("sprintId").toString()) : null;
        String title = (String) request.get("title");
        String type = (String) request.getOrDefault("type", "TASK");
        String priority = (String) request.getOrDefault("priority", "MEDIUM");
        Integer storyPoints = request.get("storyPoints") != null
                ? Integer.valueOf(request.get("storyPoints").toString()) : null;

        String description = taskAiService.generateDescription(
                projectId, sprintId, title, type, priority, storyPoints);
        return ResponseEntity.ok(ApiResponse.ok(description));
    }
}
