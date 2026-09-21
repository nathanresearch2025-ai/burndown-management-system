package com.burndown.burndown.client;

import com.burndown.common.dto.ApiResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class TaskServiceClientFallback implements TaskServiceClient {

    @Override
    public ApiResponse<BigDecimal> getRemainingPoints(Long sprintId) {
        return ApiResponse.ok(BigDecimal.ZERO);
    }

    @Override
    public ApiResponse<BigDecimal> getCompletedPoints(Long sprintId) {
        return ApiResponse.ok(BigDecimal.ZERO);
    }

    @Override
    public ApiResponse<BigDecimal> getTotalPoints(Long sprintId) {
        return ApiResponse.ok(BigDecimal.ZERO);
    }

    @Override
    public ApiResponse<Map<String, Long>> getStatusCounts(Long sprintId) {
        return ApiResponse.ok(Map.of());
    }
}
