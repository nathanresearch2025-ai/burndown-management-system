package com.burndown.aiagent.standup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StandupQueryRequest {
    @NotBlank(message = "Question is required")
    private String question;

    @NotNull(message = "Project ID is required")
    private Long projectId;

    private Long sprintId;

    private String timezone = "Asia/Shanghai";
}
