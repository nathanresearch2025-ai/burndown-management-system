package com.burndown.burndown.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "burndown_points", schema = "pg_burndown")
@EntityListeners(AuditingEntityListener.class)
public class BurndownPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sprint_id", nullable = false)
    private Long sprintId;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(name = "remaining_points", precision = 10, scale = 2)
    private BigDecimal remainingPoints;

    @Column(name = "completed_points", precision = 10, scale = 2)
    private BigDecimal completedPoints;

    @Column(name = "total_points", precision = 10, scale = 2)
    private BigDecimal totalPoints;

    @Column(name = "ideal_remaining", precision = 10, scale = 2)
    private BigDecimal idealRemaining;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
