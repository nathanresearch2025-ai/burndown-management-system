package com.burndown.repository;

import com.burndown.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findBySprintId(Long sprintId);
    List<Task> findByProjectId(Long projectId);
    List<Task> findByAssigneeId(Long assigneeId);

    @Query(value = "SELECT MAX(CAST(SUBSTRING(task_key FROM '[0-9]+$') AS INTEGER)) FROM tasks WHERE project_id = ?1", nativeQuery = true)
    Integer findMaxTaskNumberByProjectId(Long projectId);
}
