package com.burndown.project.service;

import com.burndown.common.dto.SprintDTO;
import com.burndown.common.exception.BusinessException;
import com.burndown.common.exception.ResourceNotFoundException;
import com.burndown.project.entity.Sprint;
import com.burndown.project.repository.SprintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SprintService {

    private final SprintRepository sprintRepository;

    @Cacheable(value = "sprints", key = "#id")
    public SprintDTO getById(Long id) {
        return toDTO(sprintRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint", id)));
    }

    public Page<SprintDTO> getByProject(Long projectId, Pageable pageable) {
        return sprintRepository.findByProjectId(projectId, pageable).map(this::toDTO);
    }

    @Cacheable(value = "sprints", key = "'active:' + #projectId")
    public SprintDTO getActiveSprint(Long projectId) {
        return sprintRepository.findByProjectIdAndStatus(projectId, "ACTIVE")
                .map(this::toDTO)
                .orElse(null);
    }

    @Transactional
    public SprintDTO create(Long projectId, Sprint sprint) {
        sprint.setProjectId(projectId);
        sprint.setStatus("PLANNED");
        return toDTO(sprintRepository.save(sprint));
    }

    @Transactional
    @CacheEvict(value = "sprints", key = "#id")
    public SprintDTO update(Long id, Sprint updates) {
        Sprint sprint = sprintRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint", id));
        if (updates.getName() != null) sprint.setName(updates.getName());
        if (updates.getGoal() != null) sprint.setGoal(updates.getGoal());
        if (updates.getStartDate() != null) sprint.setStartDate(updates.getStartDate());
        if (updates.getEndDate() != null) sprint.setEndDate(updates.getEndDate());
        if (updates.getTotalCapacity() != null) sprint.setTotalCapacity(updates.getTotalCapacity());
        return toDTO(sprintRepository.save(sprint));
    }

    @Transactional
    @CacheEvict(value = "sprints", allEntries = true)
    public SprintDTO startSprint(Long id) {
        Sprint sprint = sprintRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint", id));
        if (!"PLANNED".equals(sprint.getStatus())) {
            throw new BusinessException("SPRINT_NOT_PLANNED",
                    "Only PLANNED sprints can be started", HttpStatus.BAD_REQUEST);
        }
        // Check no active sprint in same project
        sprintRepository.findByProjectIdAndStatus(sprint.getProjectId(), "ACTIVE")
                .ifPresent(s -> { throw new BusinessException("ACTIVE_SPRINT_EXISTS",
                        "Project already has an active sprint", HttpStatus.CONFLICT); });
        sprint.setStatus("ACTIVE");
        sprint.setStartedAt(LocalDateTime.now());
        return toDTO(sprintRepository.save(sprint));
    }

    @Transactional
    @CacheEvict(value = "sprints", allEntries = true)
    public SprintDTO completeSprint(Long id) {
        Sprint sprint = sprintRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint", id));
        if (!"ACTIVE".equals(sprint.getStatus())) {
            throw new BusinessException("SPRINT_NOT_ACTIVE",
                    "Only ACTIVE sprints can be completed", HttpStatus.BAD_REQUEST);
        }
        sprint.setStatus("COMPLETED");
        sprint.setCompletedAt(LocalDateTime.now());
        return toDTO(sprintRepository.save(sprint));
    }

    public SprintDTO toDTO(Sprint s) {
        SprintDTO dto = new SprintDTO();
        dto.setId(s.getId());
        dto.setProjectId(s.getProjectId());
        dto.setName(s.getName());
        dto.setGoal(s.getGoal());
        dto.setStartDate(s.getStartDate());
        dto.setEndDate(s.getEndDate());
        dto.setStatus(s.getStatus());
        dto.setTotalCapacity(s.getTotalCapacity());
        dto.setCommittedPoints(s.getCommittedPoints());
        dto.setCompletedPoints(s.getCompletedPoints());
        dto.setCreatedAt(s.getCreatedAt());
        return dto;
    }
}
