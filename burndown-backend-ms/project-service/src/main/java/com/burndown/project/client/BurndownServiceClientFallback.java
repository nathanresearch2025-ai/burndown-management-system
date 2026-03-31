package com.burndown.project.client;

import com.burndown.common.dto.ApiResponse;
import com.burndown.common.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class BurndownServiceClientFallback implements BurndownServiceClient {

    @Override
    public ApiResponse<Void> recordDailyPoint(Long sprintId) {
        throw new BusinessException("BURNDOWN_SERVICE_UNAVAILABLE",
                "burndown-service is unavailable, cannot initialize burndown baseline",
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Override
    public ApiResponse<Void> deleteSprintPoints(Long sprintId) {
        throw new BusinessException("BURNDOWN_SERVICE_UNAVAILABLE",
                "burndown-service is unavailable, cannot delete burndown points",
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
    }
}
