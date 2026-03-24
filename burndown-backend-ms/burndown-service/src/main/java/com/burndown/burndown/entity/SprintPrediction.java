package com.burndown.burndown.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sprint_predictions", schema = "pg_burndown")
@EntityListeners(AuditingEntityListener.class)
public class SprintPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sprint_id", nullable = false, unique = true)
    private Long sprintId;

    @Column(name = "predicted_completion_rate", precision = 5, scale = 2)
    private BigDecimal predictedCompletionRate;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "ml_model", length = 100)
    private String mlModel;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
