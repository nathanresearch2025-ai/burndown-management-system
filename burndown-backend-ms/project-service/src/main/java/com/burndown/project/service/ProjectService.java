package com.burndown.project.service;

import com.burndown.common.dto.ProjectDTO;
import com.burndown.common.exception.BusinessException;
import com.burndown.common.exception.ResourceNotFoundException;
import com.burndown.project.entity.Project;
import com.burndown.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;

    @Cacheable(value = "projects", key = "#id")
    public ProjectDTO getById(Long id) {
        return toDTO(projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id)));
    }

    @Cacheable(value = "projects", key = "'user:' + #ownerId + ':' + #pageable.pageNumber")
    public Page<ProjectDTO> getByOwner(Long ownerId, Pageable pageable) {
        return projectRepository.findByOwnerId(ownerId, pageable).map(this::toDTO);
    }

    @Transactional
    @CacheEvict(value = "projects", key = "'user:' + #ownerId + ':*'")
    public ProjectDTO create(Long ownerId, Project project) {
        if (projectRepository.existsByProjectKey(project.getProjectKey())) {
            throw new BusinessException("PROJECT_KEY_TAKEN",
                    "Project key already in use", HttpStatus.CONFLICT);
        }
        project.setOwnerId(ownerId);
        return toDTO(projectRepository.save(project));
    }

    @Transactional
    @CacheEvict(value = "projects", key = "#id")
    public ProjectDTO update(Long id, Project updates) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
        if (updates.getName() != null) project.setName(updates.getName());
        if (updates.getDescription() != null) project.setDescription(updates.getDescription());
        if (updates.getStatus() != null) project.setStatus(updates.getStatus());
        if (updates.getStartDate() != null) project.setStartDate(updates.getStartDate());
        if (updates.getEndDate() != null) project.setEndDate(updates.getEndDate());
        return toDTO(projectRepository.save(project));
    }

    @Transactional
    @CacheEvict(value = "projects", key = "#id")
    public void delete(Long id) {
        if (!projectRepository.existsById(id)) {
            throw new ResourceNotFoundException("Project", id);
        }
        projectRepository.deleteById(id);
    }

    public ProjectDTO toDTO(Project p) {
        ProjectDTO dto = new ProjectDTO();
        dto.setId(p.getId());
        dto.setName(p.getName());
        dto.setDescription(p.getDescription());
        dto.setProjectKey(p.getProjectKey());
        dto.setType(p.getType());
        dto.setStatus(p.getStatus());
        dto.setVisibility(p.getVisibility());
        dto.setOwnerId(p.getOwnerId());
        dto.setStartDate(p.getStartDate());
        dto.setEndDate(p.getEndDate());
        dto.setCreatedAt(p.getCreatedAt());
        return dto;
    }
}
