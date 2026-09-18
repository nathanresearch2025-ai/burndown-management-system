package com.burndown.project.saga.step;

import com.burndown.project.client.BurndownServiceClient;
import com.burndown.project.saga.SagaContext;
import com.burndown.project.saga.SagaStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InitBurndownStep implements SagaStep {

    private final BurndownServiceClient burndownServiceClient;

    @Override
    public String name() {
        return "INIT_BURNDOWN";
    }

    @Override
    public void execute(SagaContext ctx) {
        // Record final burndown point for completed sprint
        burndownServiceClient.recordDailyPoint(ctx.getSprintId());
        // Initialize baseline for next sprint
        burndownServiceClient.recordDailyPoint(ctx.getNextSprintId());
        log.info("[{}] Initialized burndown for sprint {} and next sprint {}",
                ctx.getSagaId(), ctx.getSprintId(), ctx.getNextSprintId());
    }

    @Override
    public void compensate(SagaContext ctx) {
        if (ctx.getNextSprintId() != null) {
            burndownServiceClient.deleteSprintPoints(ctx.getNextSprintId());
            log.info("[{}] Deleted burndown points for next sprint {}", ctx.getSagaId(), ctx.getNextSprintId());
        }
    }
}
