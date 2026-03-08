package com.burndown.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ai_task_generation_logs")
@EntityListeners(AuditingEntityListener.class)
public class AiTaskGenerationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 500)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", columnDefinition = "jsonb", nullable = false)
    private String requestPayload;

    @Column(name = "response_description", columnDefinition = "TEXT")
    private String responseDescription;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "similar_task_ids", columnDefinition = "jsonb")
    private String similarTaskIds;

    @Column(name = "is_accepted")
    private Boolean isAccepted;

    @Column(name = "feedback_rating")
    private Integer feedbackRating;

    @Column(name = "feedback_comment", columnDefinition = "TEXT")
    private String feedbackComment;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
