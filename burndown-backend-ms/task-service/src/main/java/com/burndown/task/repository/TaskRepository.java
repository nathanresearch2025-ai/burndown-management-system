package com.burndown.task.repository;

import com.burndown.task.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {
    Page<Task> findBySprintId(Long sprintId, Pageable pageable);
    Page<Task> findByProjectId(Long projectId, Pageable pageable);
    Page<Task> findBySprintIdAndStatus(Long sprintId, String status, Pageable pageable);
    List<Task> findBySprintIdAndStatusNot(Long sprintId, String status);
    Optional<Task> findByTaskKey(String taskKey);

    @Query("SELECT SUM(t.storyPoints) FROM Task t WHERE t.sprintId = :sprintId AND t.status != 'DONE'")
    java.math.BigDecimal sumRemainingPointsBySprintId(@Param("sprintId") Long sprintId);

    @Query("SELECT SUM(t.storyPoints) FROM Task t WHERE t.sprintId = :sprintId AND t.status = 'DONE'")
    java.math.BigDecimal sumCompletedPointsBySprintId(@Param("sprintId") Long sprintId);

    @Query("SELECT SUM(t.storyPoints) FROM Task t WHERE t.sprintId = :sprintId")
    java.math.BigDecimal sumTotalPointsBySprintId(@Param("sprintId") Long sprintId);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.sprintId = :sprintId AND t.status = :status")
    long countBySprintIdAndStatus(@Param("sprintId") Long sprintId, @Param("status") String status);
}
