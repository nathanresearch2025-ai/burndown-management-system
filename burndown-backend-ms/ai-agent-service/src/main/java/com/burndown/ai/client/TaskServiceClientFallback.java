package com.burndown.ai.client;

import com.burndown.common.dto.ApiResponse;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TaskServiceClientFallback implements TaskServiceClient {

    @Override
    public ApiResponse<Map<String, Object>> getTasksBySprint(Long sprintId, int page, int size) {
        return ApiResponse.ok(Map.of());
    }

    @Override
    public ApiResponse<Map<String, Object>> getTaskById(Long id) {
        return ApiResponse.ok(Map.of());
    }
}
