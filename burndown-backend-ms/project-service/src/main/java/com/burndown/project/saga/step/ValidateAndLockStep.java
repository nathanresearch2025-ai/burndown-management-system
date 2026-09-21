package com.burndown.project.saga.step;

import com.burndown.common.exception.BusinessException;
import com.burndown.common.exception.ResourceNotFoundException;
import com.burndown.project.entity.Sprint;
import com.burndown.project.repository.SagaInstanceRepository;
import com.burndown.project.repository.SprintRepository;
import com.burndown.project.saga.SagaContext;
import com.burndown.project.saga.SagaStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ValidateAndLockStep implements SagaStep {

    private final SprintRepository sprintRepository;
    private final SagaInstanceRepository sagaInstanceRepository;

    @Override
    public String name() {
        return "VALIDATE_AND_LOCK";
    }

    @Override
    public void execute(SagaContext ctx) {
        Sprint sprint = sprintRepository.findById(ctx.getSprintId())
                .orElseThrow(() -> new ResourceNotFoundException("Sprint", ctx.getSprintId()));

        if (!"ACTIVE".equals(sprint.getStatus())) {
            throw new BusinessException("SPRINT_NOT_ACTIVE",
                    "Sprint " + ctx.getSprintId() + " is not ACTIVE", HttpStatus.BAD_REQUEST);
        }

        boolean alreadyRunning = sagaInstanceRepository
                .findBySprintIdAndStatusIn(ctx.getSprintId(), List.of("STARTED", "IN_PROGRESS"))
                .filter(s -> !s.getId().equals(ctx.getSagaId()))
                .isPresent();
        if (alreadyRunning) {
            throw new BusinessException("SAGA_ALREADY_RUNNING",
                    "A Saga is already running for sprint " + ctx.getSprintId(), HttpStatus.CONFLICT);
        }

        log.info("[{}] ValidateAndLockStep passed for sprint {}", ctx.getSagaId(), ctx.getSprintId());
    }

    @Override
    public void compensate(SagaContext ctx) {
        // No-op: nothing was changed in this step
        log.info("[{}] ValidateAndLockStep compensate — no-op", ctx.getSagaId());
    }
}
