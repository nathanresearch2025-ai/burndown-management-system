package com.burndown.service;

import com.burndown.dto.CreateSprintRequest;
import com.burndown.entity.Sprint;
import com.burndown.repository.SprintRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SprintService {

    private final SprintRepository sprintRepository;

    public SprintService(SprintRepository sprintRepository) {
        this.sprintRepository = sprintRepository;
    }

    @Transactional
    public Sprint createSprint(CreateSprintRequest request) {
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new RuntimeException("End date must be after start date");
        }

        Sprint sprint = new Sprint();
        sprint.setProjectId(request.getProjectId());
        sprint.setName(request.getName());
        sprint.setGoal(request.getGoal());
        sprint.setStartDate(request.getStartDate());
        sprint.setEndDate(request.getEndDate());
        sprint.setTotalCapacity(request.getTotalCapacity());
        sprint.setStatus(Sprint.SprintStatus.PLANNED);

        return sprintRepository.save(sprint);
    }

    public List<Sprint> getSprintsByProject(Long projectId) {
        return sprintRepository.findByProjectIdOrderByStartDateDesc(projectId);
    }

    public Sprint getSprintById(Long id) {
        return sprintRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sprint not found"));
    }

    @Transactional
    public Sprint startSprint(Long sprintId) {
        Sprint sprint = getSprintById(sprintId);
        if (sprint.getStatus() != Sprint.SprintStatus.PLANNED) {
            throw new RuntimeException("Only planned sprints can be started");
        }
        sprint.setStatus(Sprint.SprintStatus.ACTIVE);
        sprint.setStartedAt(LocalDateTime.now());
        return sprintRepository.save(sprint);
    }

    @Transactional
    public Sprint completeSprint(Long sprintId) {
        Sprint sprint = getSprintById(sprintId);
        if (sprint.getStatus() != Sprint.SprintStatus.ACTIVE) {
            throw new RuntimeException("Only active sprints can be completed");
        }
        sprint.setStatus(Sprint.SprintStatus.COMPLETED);
        sprint.setCompletedAt(LocalDateTime.now());
        return sprintRepository.save(sprint);
    }
}
