package com.burndown.burndown.client;

import com.burndown.common.dto.ApiResponse;
import com.burndown.common.dto.TaskEventDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.Map;

@FeignClient(name = "task-service", fallback = TaskServiceClientFallback.class)
public interface TaskServiceClient {

    @GetMapping("/api/v1/tasks/sprint/{sprintId}/remaining-points")
    ApiResponse<BigDecimal> getRemainingPoints(@PathVariable Long sprintId);

    @GetMapping("/api/v1/tasks/sprint/{sprintId}/completed-points")
    ApiResponse<BigDecimal> getCompletedPoints(@PathVariable Long sprintId);

    @GetMapping("/api/v1/tasks/sprint/{sprintId}/total-points")
    ApiResponse<BigDecimal> getTotalPoints(@PathVariable Long sprintId);

    @GetMapping("/api/v1/tasks/sprint/{sprintId}/status-counts")
    ApiResponse<Map<String, Long>> getStatusCounts(@PathVariable Long sprintId);
}
