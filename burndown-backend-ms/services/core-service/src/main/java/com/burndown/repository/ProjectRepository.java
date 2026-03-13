package com.burndown.repository;

import com.burndown.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findByProjectKey(String projectKey);
    boolean existsByProjectKey(String projectKey);
    List<Project> findByOwnerId(Long ownerId);
}
