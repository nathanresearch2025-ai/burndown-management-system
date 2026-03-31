package com.burndown.project.saga;

import lombok.Data;

@Data
public class SagaContext {

    private String sagaId;
    private Long sprintId;
    private Long projectId;
    private Long nextSprintId;
    private String nextSprintName;

    public SagaContext(String sagaId, Long sprintId, Long projectId) {
        this.sagaId = sagaId;
        this.sprintId = sprintId;
        this.projectId = projectId;
    }
}
