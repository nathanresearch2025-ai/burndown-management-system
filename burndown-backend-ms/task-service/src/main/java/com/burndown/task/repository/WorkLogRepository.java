package com.burndown.task.repository;

import com.burndown.task.entity.WorkLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface WorkLogRepository extends JpaRepository<WorkLog, Long> {
    Page<WorkLog> findByTaskId(Long taskId, Pageable pageable);
    List<WorkLog> findByTaskIdAndLogDateBetween(Long taskId, LocalDate start, LocalDate end);

    @Query("SELECT SUM(w.timeSpent) FROM WorkLog w WHERE w.taskId = :taskId")
    BigDecimal sumTimeSpentByTaskId(@Param("taskId") Long taskId);
}
