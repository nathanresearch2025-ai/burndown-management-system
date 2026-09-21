package com.burndown.project.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SagaInstanceDTO {
    private String id;
    private String sagaType;
    private String status;
    private String currentStep;
    private Long sprintId;
    private Long projectId;
    private Long nextSprintId;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<SagaStepLogDTO> steps;

    @Data
    public static class SagaStepLogDTO {
        private String stepName;
        private String stepStatus;
        private String errorMsg;
        private LocalDateTime executedAt;
    }
}
