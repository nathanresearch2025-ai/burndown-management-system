package com.burndown.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateProjectRequest {
    @NotBlank(message = "Project name is required")
    private String name;

    private String description;

    @NotBlank(message = "Project key is required")
    private String projectKey;

    @NotNull(message = "Project type is required")
    private String type;

    private String visibility = "PRIVATE";

    private LocalDate startDate;

    private LocalDate endDate;
}
