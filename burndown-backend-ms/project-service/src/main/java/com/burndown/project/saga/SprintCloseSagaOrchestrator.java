package com.burndown.project.saga;

import com.burndown.common.exception.BusinessException;
import com.burndown.project.entity.SagaInstance;
import com.burndown.project.entity.SagaStepLog;
import com.burndown.project.repository.SagaInstanceRepository;
import com.burndown.project.repository.SagaStepLogRepository;
import com.burndown.project.saga.step.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SprintCloseSagaOrchestrator {

    private final SagaInstanceRepository sagaInstanceRepository;
    private final SagaStepLogRepository sagaStepLogRepository;
    private final ValidateAndLockStep validateAndLockStep;
    private final CompleteCurrentSprintStep completeCurrentSprintStep;
    private final CreateNextSprintStep createNextSprintStep;
    private final MigrateTasksStep migrateTasksStep;
    private final InitBurndownStep initBurndownStep;

    public SagaInstance start(Long sprintId, Long projectId, String nextSprintName) {
        SagaInstance saga = new SagaInstance();
        saga.setId(UUID.randomUUID().toString());
        saga.setSagaType("SPRINT_CLOSE");
        saga.setStatus("STARTED");
        saga.setSprintId(sprintId);
        saga.setProjectId(projectId);
        saga.setContextJson("{\"nextSprintName\":\"" + (nextSprintName != null ? nextSprintName : "") + "\"}");
        sagaInstanceRepository.save(saga);

        SagaContext ctx = new SagaContext(saga.getId(), sprintId, projectId);
        ctx.setNextSprintName(nextSprintName);

        //  实际用到的设计模式
        //
        //  ┌───────────────────────┬────────────────────────────────────────────────────────────────┐
        //  │         模式          │                           体现在哪里                           │
        //  ├───────────────────────┼────────────────────────────────────────────────────────────────┤
        //  │ 命令模式              │ SagaStep 接口将"执行"和"撤销"封装成对象，orchestrator 统一调用 │
        //  ├───────────────────────┼────────────────────────────────────────────────────────────────┤
        //  │ 模板方法 / 编排器模式 │ SprintCloseSagaOrchestrator 定义了执行顺序和补偿顺序的骨架     │
        //  ├───────────────────────┼────────────────────────────────────────────────────────────────┤
        //  │ Saga 模式             │ 分布式事务的核心模式，每步可补偿，保证最终一致性               │
        //  └───────────────────────┴────────────────────────────────────────────────────────────────┘
        //
        //  ---
        //  总结
        //
        //  这是 Saga 编排模式（Orchestration-based Saga），SagaStep
        //  接口的设计借鉴了命令模式（将操作对象化、支持撤销）。策略模式强调"选一个"，这里强调"全部按序执行+失败可回滚"，两者意图不同。
        List<SagaStep> steps = List.of(
                validateAndLockStep,
                completeCurrentSprintStep,
                createNextSprintStep,
                migrateTasksStep,
                initBurndownStep
        );

        int executedUpTo = -1;
        try {
            for (int i = 0; i < steps.size(); i++) {
                SagaStep step = steps.get(i);
                updateSagaStatus(saga.getId(), "IN_PROGRESS", step.name());
                step.execute(ctx);
                // persist nextSprintId after step 3 creates it
                if (i == 2 && ctx.getNextSprintId() != null) {
                    SagaInstance s = sagaInstanceRepository.findById(saga.getId()).orElse(saga);
                    s.setNextSprintId(ctx.getNextSprintId());
                    sagaInstanceRepository.save(s);
                }
                writeStepLog(saga.getId(), step.name(), "SUCCESS", null);
                executedUpTo = i;
            }
            updateSagaStatus(saga.getId(), "SUCCEEDED", null);
            log.info("[{}] Saga SUCCEEDED", saga.getId());
        } catch (BusinessException e) {
            log.warn("[{}] Business validation failed: {}", saga.getId(), e.getMessage());
            updateSagaStatus(saga.getId(), "FAILED", null);
            sagaInstanceRepository.findById(saga.getId()).ifPresent(s -> {
                s.setFailureReason(e.getMessage());
                sagaInstanceRepository.save(s);
            });
            throw e;
        } catch (Exception e) {
            log.error("[{}] Step failed: {}", saga.getId(), e.getMessage());
            writeStepLog(saga.getId(),
                    executedUpTo >= 0 ? steps.get(executedUpTo + 1 < steps.size() ? executedUpTo + 1 : executedUpTo).name() : "UNKNOWN",
                    "FAILED", e.getMessage());
            compensate(saga.getId(), ctx, steps, executedUpTo);
        }

        return sagaInstanceRepository.findById(saga.getId()).orElse(saga);
    }

    private void compensate(String sagaId, SagaContext ctx, List<SagaStep> steps, int executedUpTo) {
        updateSagaStatus(sagaId, "COMPENSATING", null);
        for (int i = executedUpTo; i >= 0; i--) {
            SagaStep step = steps.get(i);
            try {
                step.compensate(ctx);
                writeStepLog(sagaId, step.name() + "_COMPENSATE", "SUCCESS", null);
            } catch (Exception ce) {
                log.error("[{}] Compensation failed for step {}: {}", sagaId, step.name(), ce.getMessage());
                writeStepLog(sagaId, step.name() + "_COMPENSATE", "FAILED", ce.getMessage());
            }
        }
        updateSagaStatus(sagaId, "COMPENSATED", null);
        log.info("[{}] Saga COMPENSATED", sagaId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateSagaStatus(String sagaId, String status, String currentStep) {
        sagaInstanceRepository.findById(sagaId).ifPresent(s -> {
            s.setStatus(status);
            if (currentStep != null) s.setCurrentStep(currentStep);
            s.setUpdatedAt(LocalDateTime.now());
            sagaInstanceRepository.save(s);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeStepLog(String sagaId, String stepName, String stepStatus, String errorMsg) {
        SagaStepLog log2 = new SagaStepLog();
        log2.setSagaId(sagaId);
        log2.setStepName(stepName);
        log2.setStepStatus(stepStatus);
        log2.setErrorMsg(errorMsg);
        log2.setExecutedAt(LocalDateTime.now());
        sagaStepLogRepository.save(log2);
    }
}
