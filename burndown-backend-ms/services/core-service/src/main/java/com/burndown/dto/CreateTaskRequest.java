package com.burndown.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateTaskRequest {
    @NotNull(message = "Project ID is required")
    private Long projectId;

    private Long sprintId;

    @NotBlank(message = "Task title is required")
    private String title;

    private String description;

    @NotNull(message = "Task type is required")
    private String type;

    private String priority = "MEDIUM";

    private BigDecimal storyPoints;

    private BigDecimal originalEstimate;

    private Long assigneeId;
}
