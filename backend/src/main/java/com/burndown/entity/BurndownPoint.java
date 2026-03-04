package com.burndown.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "burndown_points", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"sprint_id", "point_date"})
})
@EntityListeners(AuditingEntityListener.class)
public class BurndownPoint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sprint_id", nullable = false)
    private Long sprintId;

    @Column(name = "point_date", nullable = false)
    private LocalDate pointDate;

    @Column(name = "ideal_remaining", nullable = false, precision = 10, scale = 2)
    private BigDecimal idealRemaining;

    @Column(name = "actual_remaining", nullable = false, precision = 10, scale = 2)
    private BigDecimal actualRemaining;

    @Column(name = "completed_points", precision = 10, scale = 2)
    private BigDecimal completedPoints = BigDecimal.ZERO;

    @Column(name = "scope_change", precision = 10, scale = 2)
    private BigDecimal scopeChange = BigDecimal.ZERO;

    @Column(name = "total_tasks")
    private Integer totalTasks = 0;

    @Column(name = "completed_tasks")
    private Integer completedTasks = 0;

    @Column(name = "in_progress_tasks")
    private Integer inProgressTasks = 0;

    @CreatedDate
    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt;
}
