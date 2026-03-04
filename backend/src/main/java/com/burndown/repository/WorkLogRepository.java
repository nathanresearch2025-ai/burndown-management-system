package com.burndown.repository;

import com.burndown.entity.WorkLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkLogRepository extends JpaRepository<WorkLog, Long> {
    List<WorkLog> findByTaskId(Long taskId);
    List<WorkLog> findByUserId(Long userId);
    Optional<WorkLog> findByTaskIdAndUserIdAndWorkDate(Long taskId, Long userId, LocalDate workDate);

    @Query("SELECT w FROM WorkLog w WHERE w.taskId = :taskId AND w.workDate <= :date ORDER BY w.workDate DESC")
    List<WorkLog> findLatestByTaskIdBeforeDate(@Param("taskId") Long taskId, @Param("date") LocalDate date);
}
