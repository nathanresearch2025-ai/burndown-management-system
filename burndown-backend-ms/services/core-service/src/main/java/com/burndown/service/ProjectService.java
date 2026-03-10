package com.burndown.service;

import com.burndown.dto.CreateProjectRequest;
import com.burndown.entity.Project;
import com.burndown.exception.BusinessException;
import com.burndown.repository.ProjectRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    @CacheEvict(value = "projects", allEntries = true)
    public Project createProject(CreateProjectRequest request, Long ownerId) {
        if (projectRepository.existsByProjectKey(request.getProjectKey())) {
            throw new BusinessException("PROJECT_KEY_EXISTS", "project.keyExists", HttpStatus.CONFLICT, request.getProjectKey());
        }

        Project project = new Project();
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setProjectKey(request.getProjectKey());
        project.setType(Project.ProjectType.valueOf(request.getType()));
        project.setVisibility(request.getVisibility());
        project.setStartDate(request.getStartDate());
        project.setEndDate(request.getEndDate());
        project.setOwnerId(ownerId);
        project.setStatus("ACTIVE");

        return projectRepository.save(project);
    }

    @Cacheable(value = "projects", key = "'owner:' + #ownerId")
    public List<Project> getProjectsByOwner(Long ownerId) {
        return projectRepository.findByOwnerId(ownerId);
    }

    public Project getProjectById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found"));
    }

    @Cacheable(value = "projects", key = "'all'")
    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }
}
