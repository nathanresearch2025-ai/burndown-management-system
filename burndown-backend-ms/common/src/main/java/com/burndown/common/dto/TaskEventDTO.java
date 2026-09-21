package com.burndown.common.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * RabbitMQ event payload for task state changes.
 * Published by task-service, consumed by burndown-service and ai-agent-service.
 */
@Data
public class TaskEventDTO {

    public enum EventType {
        TASK_CREATED, TASK_STATUS_CHANGED, TASK_DELETED
    }

    private EventType eventType;
    private Long taskId;
    private String taskKey;
    private Long sprintId;
    private Long projectId;
    private String oldStatus;
    private String newStatus;
    private BigDecimal storyPoints;
    private LocalDateTime occurredAt = LocalDateTime.now();
}
