package com.burndown.project.saga.step;

import com.burndown.common.exception.ResourceNotFoundException;
import com.burndown.project.entity.Sprint;
import com.burndown.project.repository.SprintRepository;
import com.burndown.project.saga.SagaContext;
import com.burndown.project.saga.SagaStep;
import com.burndown.project.service.SprintService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompleteCurrentSprintStep implements SagaStep {

    private final SprintService sprintService;
    private final SprintRepository sprintRepository;

    @Override
    public String name() {
        return "COMPLETE_CURRENT_SPRINT";
    }

    @Override
    public void execute(SagaContext ctx) {
        sprintService.completeSprint(ctx.getSprintId());
        log.info("[{}] Sprint {} marked COMPLETED", ctx.getSagaId(), ctx.getSprintId());
    }

    @Override
    public void compensate(SagaContext ctx) {
        Sprint sprint = sprintRepository.findById(ctx.getSprintId())
                .orElseThrow(() -> new ResourceNotFoundException("Sprint", ctx.getSprintId()));
        sprint.setStatus("ACTIVE");
        sprint.setCompletedAt(null);
        sprintRepository.save(sprint);
        log.info("[{}] Sprint {} rolled back to ACTIVE", ctx.getSagaId(), ctx.getSprintId());
    }
}
