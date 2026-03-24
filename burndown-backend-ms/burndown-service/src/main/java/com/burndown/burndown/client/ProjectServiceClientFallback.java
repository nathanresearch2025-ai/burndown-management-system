package com.burndown.burndown.client;

import com.burndown.common.dto.ApiResponse;
import com.burndown.common.dto.SprintDTO;
import org.springframework.stereotype.Component;

@Component
public class ProjectServiceClientFallback implements ProjectServiceClient {

    @Override
    public ApiResponse<SprintDTO> getSprint(Long id) {
        return ApiResponse.ok(SprintDTO.empty(id));
    }
}
