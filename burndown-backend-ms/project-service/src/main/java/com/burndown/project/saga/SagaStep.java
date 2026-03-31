package com.burndown.project.saga;

public interface SagaStep {
    String name();
    void execute(SagaContext ctx);
    void compensate(SagaContext ctx);
}
