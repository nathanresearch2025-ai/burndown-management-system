package com.burndown.repository;

import com.burndown.entity.Sprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SprintRepository extends JpaRepository<Sprint, Long> {
    List<Sprint> findByProjectId(Long projectId);
    List<Sprint> findByProjectIdOrderByStartDateDesc(Long projectId);

    /**
     * Fetch the most recently completed Sprints for a project (used to calculate historical velocity).
     */
    List<Sprint> findTop5ByProjectIdAndStatusOrderByEndDateDesc(Long projectId, Sprint.SprintStatus status);
}
