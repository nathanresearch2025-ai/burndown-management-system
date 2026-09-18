package com.burndown.project.client;

import com.burndown.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "burndown-service", fallback = BurndownServiceClientFallback.class)
public interface BurndownServiceClient {

    @PostMapping("/api/v1/burndown/sprint/{sprintId}/record")
    ApiResponse<Void> recordDailyPoint(@PathVariable Long sprintId);

    @DeleteMapping("/api/v1/internal/burndown/sprint/{sprintId}")
    ApiResponse<Void> deleteSprintPoints(@PathVariable Long sprintId);
}
