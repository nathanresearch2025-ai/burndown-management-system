package com.burndown.project.repository;

import com.burndown.project.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    Page<Project> findByOwnerId(Long ownerId, Pageable pageable);
    Page<Project> findByOwnerIdAndStatus(Long ownerId, String status, Pageable pageable);
    Optional<Project> findByProjectKey(String projectKey);
    boolean existsByProjectKey(String projectKey);
}
