package com.burndown.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class LogWorkRequest {
    @NotNull(message = "Task ID is required")
    private Long taskId;

    @NotNull(message = "Work date is required")
    private LocalDate workDate;

    @NotNull(message = "Time spent is required")
    @Positive(message = "Time spent must be positive")
    private BigDecimal timeSpent;

    @NotNull(message = "Remaining estimate is required")
    private BigDecimal remainingEstimate;

    private String comment;
}
