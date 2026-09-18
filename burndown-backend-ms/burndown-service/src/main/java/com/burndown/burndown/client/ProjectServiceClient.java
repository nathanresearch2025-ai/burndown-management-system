package com.burndown.burndown.client;

import com.burndown.common.dto.ApiResponse;
import com.burndown.common.dto.SprintDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "project-service", fallback = ProjectServiceClientFallback.class)
public interface ProjectServiceClient {

    @GetMapping("/api/v1/sprints/{id}")
    ApiResponse<SprintDTO> getSprint(@PathVariable Long id);
}
