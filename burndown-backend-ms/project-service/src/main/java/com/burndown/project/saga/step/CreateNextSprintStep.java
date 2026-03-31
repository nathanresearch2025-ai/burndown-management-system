package com.burndown.project.saga.step;

import com.burndown.common.exception.ResourceNotFoundException;
import com.burndown.project.entity.Sprint;
import com.burndown.project.repository.SprintRepository;
import com.burndown.project.saga.SagaContext;
import com.burndown.project.saga.SagaStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreateNextSprintStep implements SagaStep {

    private final SprintRepository sprintRepository;

    @Override
    public String name() {
        return "CREATE_NEXT_SPRINT";
    }

    @Override
    public void execute(SagaContext ctx) {
        Sprint current = sprintRepository.findById(ctx.getSprintId())
                .orElseThrow(() -> new ResourceNotFoundException("Sprint", ctx.getSprintId()));

        Sprint next = new Sprint();
        next.setProjectId(ctx.getProjectId());
        next.setName(ctx.getNextSprintName() != null
                ? ctx.getNextSprintName()
                : current.getName() + " (Next)");
        next.setGoal("Carry-over from " + current.getName());
        next.setStatus("PLANNED");
        next.setStartDate(LocalDate.now());
        next.setEndDate(LocalDate.now().plusDays(14));

        Sprint saved = sprintRepository.save(next);
        ctx.setNextSprintId(saved.getId());
        log.info("[{}] Created next sprint {} (id={})", ctx.getSagaId(), saved.getName(), saved.getId());
    }

    @Override
    public void compensate(SagaContext ctx) {
        if (ctx.getNextSprintId() != null) {
            sprintRepository.deleteById(ctx.getNextSprintId());
            log.info("[{}] Deleted next sprint {}", ctx.getSagaId(), ctx.getNextSprintId());
        }
    }
}
