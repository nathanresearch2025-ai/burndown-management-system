package com.burndown.task.client;

import com.burndown.common.dto.ApiResponse;
import com.burndown.common.dto.ProjectDTO;
import com.burndown.common.dto.SprintDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "project-service", fallback = ProjectServiceClientFallback.class)
public interface ProjectServiceClient {

    @GetMapping("/api/v1/projects/{id}")
    ApiResponse<ProjectDTO> getProject(@PathVariable Long id);

    @GetMapping("/api/v1/sprints/{id}")
    ApiResponse<SprintDTO> getSprint(@PathVariable Long id);

    @GetMapping("/api/v1/sprints/project/{projectId}/active")
    ApiResponse<SprintDTO> getActiveSprint(@PathVariable Long projectId);
}
