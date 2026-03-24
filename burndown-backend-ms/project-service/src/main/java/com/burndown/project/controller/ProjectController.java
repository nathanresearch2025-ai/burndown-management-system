package com.burndown.project.controller;

import com.burndown.common.context.UserContext;
import com.burndown.common.dto.ApiResponse;
import com.burndown.common.dto.ProjectDTO;
import com.burndown.project.entity.Project;
import com.burndown.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectDTO>> getProject(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProjectDTO>>> getProjects(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                projectService.getByOwner(userId, PageRequest.of(page, size))));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectDTO>> createProject(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody Project project) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(projectService.create(userId, project)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectDTO>> updateProject(
            @PathVariable Long id,
            @RequestBody Project updates) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.update(id, updates)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProject(@PathVariable Long id) {
        projectService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Project deleted"));
    }
}
