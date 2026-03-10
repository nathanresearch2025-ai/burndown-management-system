package com.burndown.controller;

import com.burndown.config.JwtTokenProvider;
import com.burndown.dto.CreateProjectRequest;
import com.burndown.entity.Project;
import com.burndown.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<Project> createProject(@Valid @RequestBody CreateProjectRequest request,
                                                  Authentication authentication) {
        JwtTokenProvider.UserPrincipal principal = (JwtTokenProvider.UserPrincipal) authentication.getPrincipal();
        Long userId = principal.getId();
        Project project = projectService.createProject(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }

    @GetMapping
    public ResponseEntity<List<Project>> getAllProjects() {
        List<Project> projects = projectService.getAllProjects();
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Project> getProjectById(@PathVariable Long id) {
        Project project = projectService.getProjectById(id);
        return ResponseEntity.ok(project);
    }

    @GetMapping("/my-projects")
    public ResponseEntity<List<Project>> getMyProjects(Authentication authentication) {
        JwtTokenProvider.UserPrincipal principal = (JwtTokenProvider.UserPrincipal) authentication.getPrincipal();
        Long userId = principal.getId();
        List<Project> projects = projectService.getProjectsByOwner(userId);
        return ResponseEntity.ok(projects);
    }
}
