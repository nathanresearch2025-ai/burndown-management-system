package com.burndown.ai.client;

import com.burndown.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "task-service", fallback = TaskServiceClientFallback.class)
public interface TaskServiceClient {

    @GetMapping("/api/v1/tasks/sprint/{sprintId}")
    ApiResponse<Map<String, Object>> getTasksBySprint(
            @PathVariable Long sprintId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size);

    @GetMapping("/api/v1/tasks/{id}")
    ApiResponse<Map<String, Object>> getTaskById(@PathVariable Long id);
}
