package com.burndown.task.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "tasks", schema = "ms_task")
@EntityListeners(AuditingEntityListener.class)
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "sprint_id")
    private Long sprintId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "task_key", unique = true, nullable = false, length = 50)
    private String taskKey;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 20)
    private String type = "TASK";

    @Column(length = 20)
    private String status = "TODO";

    @Column(length = 20)
    private String priority = "MEDIUM";

    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(name = "reporter_id")
    private Long reporterId;

    @Column(name = "story_points", precision = 5, scale = 1)
    private BigDecimal storyPoints;

    @Column(name = "original_estimate", precision = 10, scale = 2)
    private BigDecimal originalEstimate;

    @Column(name = "remaining_estimate", precision = 10, scale = 2)
    private BigDecimal remainingEstimate;

    @Column(name = "time_spent", precision = 10, scale = 2)
    private BigDecimal timeSpent = BigDecimal.ZERO;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(name = "original_sprint_id")
    private Long originalSprintId;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
