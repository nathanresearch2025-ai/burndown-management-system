package com.burndown.project.saga.step;

import com.burndown.project.client.TaskServiceClient;
import com.burndown.project.saga.SagaContext;
import com.burndown.project.saga.SagaStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MigrateTasksStep implements SagaStep {

    private final TaskServiceClient taskServiceClient;

    @Override
    public String name() {
        return "MIGRATE_TASKS";
    }

    @Override
    public void execute(SagaContext ctx) {
        var response = taskServiceClient.migrateUndoneTasks(ctx.getSprintId(), ctx.getNextSprintId());
        int count = response.getData() != null ? response.getData() : 0;
        log.info("[{}] Migrated {} undone tasks from sprint {} to sprint {}",
                ctx.getSagaId(), count, ctx.getSprintId(), ctx.getNextSprintId());
    }

    @Override
    public void compensate(SagaContext ctx) {
        var response = taskServiceClient.compensateMigratedTasks(ctx.getSprintId());
        int count = response.getData() != null ? response.getData() : 0;
        log.info("[{}] Compensated {} tasks back to sprint {}",
                ctx.getSagaId(), count, ctx.getSprintId());
    }
}
