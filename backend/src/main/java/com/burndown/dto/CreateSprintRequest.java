package com.burndown.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateSprintRequest {
    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotBlank(message = "Sprint name is required")
    private String name;

    private String goal;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    private BigDecimal totalCapacity;
}
