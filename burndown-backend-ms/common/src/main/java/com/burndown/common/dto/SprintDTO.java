package com.burndown.common.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SprintDTO {
    private Long id;
    private Long projectId;
    private String name;
    private String goal;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private BigDecimal totalCapacity;
    private BigDecimal committedPoints;
    private BigDecimal completedPoints;
    private LocalDateTime createdAt;

    public static SprintDTO empty(Long sprintId) {
        SprintDTO dto = new SprintDTO();
        dto.setId(sprintId);
        return dto;
    }
}
