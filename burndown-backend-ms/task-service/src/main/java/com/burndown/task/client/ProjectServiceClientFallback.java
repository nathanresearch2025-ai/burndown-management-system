package com.burndown.task.client;

import com.burndown.common.dto.ApiResponse;
import com.burndown.common.dto.ProjectDTO;
import com.burndown.common.dto.SprintDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProjectServiceClientFallback implements ProjectServiceClient {

    @Override
    public ApiResponse<ProjectDTO> getProject(Long id) {
        log.warn("project-service unavailable, returning fallback for project {}", id);
        return ApiResponse.ok(ProjectDTO.empty(id));
    }

    @Override
    public ApiResponse<SprintDTO> getSprint(Long id) {
        log.warn("project-service unavailable, returning fallback for sprint {}", id);
        return ApiResponse.ok(SprintDTO.empty(id));
    }

    @Override
    public ApiResponse<SprintDTO> getActiveSprint(Long projectId) {
        log.warn("project-service unavailable, no active sprint for project {}", projectId);
        return ApiResponse.ok(null);
    }
}
