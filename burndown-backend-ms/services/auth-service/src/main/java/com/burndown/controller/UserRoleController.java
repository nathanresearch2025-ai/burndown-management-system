package com.burndown.controller;

import com.burndown.dto.AssignRolesRequest;
import com.burndown.dto.PermissionResponse;
import com.burndown.dto.RoleResponse;
import com.burndown.service.UserRoleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/users")
public class UserRoleController {

    private final UserRoleService userRoleService;

    public UserRoleController(UserRoleService userRoleService) {
        this.userRoleService = userRoleService;
    }

    @GetMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('ROLE:ASSIGN') or #userId == authentication.principal.id")
    public ResponseEntity<List<RoleResponse>> getUserRoles(@PathVariable Long userId) {
        List<RoleResponse> roles = userRoleService.getUserRoles(userId);
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/{userId}/permissions")
    @PreAuthorize("hasAuthority('ROLE:ASSIGN') or #userId == authentication.principal.id")
    public ResponseEntity<Set<PermissionResponse>> getUserPermissions(@PathVariable Long userId) {
        Set<PermissionResponse> permissions = userRoleService.getUserPermissions(userId);
        return ResponseEntity.ok(permissions);
    }

    @PostMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('ROLE:ASSIGN')")
    public ResponseEntity<Void> assignRoles(
            @PathVariable Long userId,
            @Valid @RequestBody AssignRolesRequest request,
            Authentication authentication) {
        Long assignedBy = ((com.burndown.config.JwtTokenProvider.UserPrincipal) authentication.getPrincipal()).getId();
        userRoleService.assignRoles(userId, request, assignedBy);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{userId}/roles/{roleId}")
    @PreAuthorize("hasAuthority('ROLE:ASSIGN')")
    public ResponseEntity<Void> removeRole(
            @PathVariable Long userId,
            @PathVariable Long roleId) {
        userRoleService.removeRole(userId, roleId);
        return ResponseEntity.noContent().build();
    }
}
