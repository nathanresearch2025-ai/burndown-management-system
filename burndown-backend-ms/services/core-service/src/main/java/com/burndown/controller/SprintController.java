package com.burndown.controller;

import com.burndown.dto.CreateSprintRequest;
import com.burndown.entity.Sprint;
import com.burndown.service.SprintService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sprints")
public class SprintController {

    private final SprintService sprintService;

    public SprintController(SprintService sprintService) {
        this.sprintService = sprintService;
    }

    @PostMapping
    public ResponseEntity<Sprint> createSprint(@Valid @RequestBody CreateSprintRequest request) {
        Sprint sprint = sprintService.createSprint(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(sprint);
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<Sprint>> getSprintsByProject(@PathVariable Long projectId) {
        List<Sprint> sprints = sprintService.getSprintsByProject(projectId);
        return ResponseEntity.ok(sprints);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Sprint> getSprintById(@PathVariable Long id) {
        Sprint sprint = sprintService.getSprintById(id);
        return ResponseEntity.ok(sprint);
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<Sprint> startSprint(@PathVariable Long id) {
        Sprint sprint = sprintService.startSprint(id);
        return ResponseEntity.ok(sprint);
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<Sprint> completeSprint(@PathVariable Long id) {
        Sprint sprint = sprintService.completeSprint(id);
        return ResponseEntity.ok(sprint);
    }
}
