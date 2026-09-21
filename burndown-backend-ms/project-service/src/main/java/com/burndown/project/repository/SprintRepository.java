package com.burndown.project.repository;

import com.burndown.project.entity.Sprint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SprintRepository extends JpaRepository<Sprint, Long> {
    List<Sprint> findByProjectId(Long projectId);
    Page<Sprint> findByProjectId(Long projectId, Pageable pageable);
    Optional<Sprint> findByProjectIdAndStatus(Long projectId, String status);
    List<Sprint> findByStatus(String status);
}
