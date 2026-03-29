package com.burndown.project.controller;

import com.burndown.common.dto.ApiResponse;
import com.burndown.common.dto.SprintDTO;
import com.burndown.project.dto.SprintCloseRequest;
import com.burndown.project.entity.Sprint;
import com.burndown.project.entity.SagaInstance;
import com.burndown.project.saga.SprintCloseSagaOrchestrator;
import com.burndown.project.service.SprintService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sprints")
@RequiredArgsConstructor
public class SprintController {

    private final SprintService sprintService;
    private final SprintCloseSagaOrchestrator sagaOrchestrator;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SprintDTO>> getSprint(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(sprintService.getById(id)));
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<ApiResponse<Page<SprintDTO>>> getSprintsByProject(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                sprintService.getByProject(projectId, PageRequest.of(page, size))));
    }

    @GetMapping("/project/{projectId}/active")
    public ResponseEntity<ApiResponse<SprintDTO>> getActiveSprint(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.ok(sprintService.getActiveSprint(projectId)));
    }

    @PostMapping("/project/{projectId}")
    public ResponseEntity<ApiResponse<SprintDTO>> createSprint(
            @PathVariable Long projectId,
            @RequestBody Sprint sprint) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(sprintService.create(projectId, sprint)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SprintDTO>> updateSprint(
            @PathVariable Long id,
            @RequestBody Sprint updates) {
        return ResponseEntity.ok(ApiResponse.ok(sprintService.update(id, updates)));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<ApiResponse<SprintDTO>> startSprint(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(sprintService.startSprint(id)));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<SprintDTO>> completeSprint(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(sprintService.completeSprint(id)));
    }

    @PostMapping("/{id}/close-and-carry-over")
    public ResponseEntity<ApiResponse<SagaInstance>> closeAndCarryOver(
            @PathVariable Long id,
            @RequestBody(required = false) SprintCloseRequest request) {
        SprintDTO sprint = sprintService.getById(id);
        String nextSprintName = request != null ? request.getNextSprintName() : null;
        SagaInstance result = sagaOrchestrator.start(id, sprint.getProjectId(), nextSprintName);
        HttpStatus status = "SUCCEEDED".equals(result.getStatus()) ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(ApiResponse.ok(result));
    }
}
