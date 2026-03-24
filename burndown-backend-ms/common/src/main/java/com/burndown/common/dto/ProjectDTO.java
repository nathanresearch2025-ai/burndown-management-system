package com.burndown.common.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProjectDTO {
    private Long id;
    private String name;
    private String description;
    private String projectKey;
    private String type;
    private String status;
    private String visibility;
    private Long ownerId;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime createdAt;

    public static ProjectDTO empty(Long projectId) {
        ProjectDTO dto = new ProjectDTO();
        dto.setId(projectId);
        return dto;
    }
}
