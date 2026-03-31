package com.burndown.project.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "saga_instances", schema = "ms_project")
public class SagaInstance {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "saga_type", nullable = false, length = 100)
    private String sagaType;

    @Column(nullable = false, length = 20)
    private String status = "STARTED";

    @Column(name = "current_step", length = 100)
    private String currentStep;

    @Column(name = "sprint_id", nullable = false)
    private Long sprintId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "next_sprint_id")
    private Long nextSprintId;

    @Column(name = "context_json", columnDefinition = "TEXT")
    private String contextJson;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
