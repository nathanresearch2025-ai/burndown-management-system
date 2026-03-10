package com.burndown.service;

import com.burndown.dto.*;
import com.burndown.entity.Permission;
import com.burndown.entity.Role;
import com.burndown.exception.BusinessException;
import com.burndown.exception.ResourceNotFoundException;
import com.burndown.repository.PermissionRepository;
import com.burndown.repository.RoleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public RoleService(RoleRepository roleRepository, PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(this::mapToRoleResponse)
                .collect(Collectors.toList());
    }

    public List<RoleResponse> getAvailableRolesForRegistration() {
        return roleRepository.findAll().stream()
                .filter(role -> role.getIsActive())
                .map(this::mapToRoleResponse)
                .collect(Collectors.toList());
    }

    public RoleResponse getRoleById(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("role.notFound"));
        return mapToRoleResponse(role);
    }

    @Transactional
    public RoleResponse createRole(CreateRoleRequest request) {
        if (roleRepository.existsByCode(request.getCode())) {
            throw new BusinessException("ROLE_CODE_EXISTS", "role.codeExists", HttpStatus.CONFLICT);
        }
        if (roleRepository.existsByName(request.getName())) {
            throw new BusinessException("ROLE_NAME_EXISTS", "role.nameExists", HttpStatus.CONFLICT);
        }

        Role role = new Role();
        role.setName(request.getName());
        role.setCode(request.getCode());
        role.setDescription(request.getDescription());
        role.setIsActive(request.getIsActive());
        role.setIsSystem(false);

        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            Set<Permission> permissions = new HashSet<>(
                    permissionRepository.findAllById(request.getPermissionIds())
            );
            role.setPermissions(permissions);
        }

        role = roleRepository.save(role);
        return mapToRoleResponse(role);
    }

    @Transactional
    public RoleResponse updateRole(Long id, UpdateRoleRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("role.notFound"));

        if (request.getName() != null) {
            role.setName(request.getName());
        }
        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }
        if (request.getIsActive() != null) {
            role.setIsActive(request.getIsActive());
        }
        if (request.getPermissionIds() != null) {
            Set<Permission> permissions = new HashSet<>(
                    permissionRepository.findAllById(request.getPermissionIds())
            );
            role.setPermissions(permissions);
        }

        role = roleRepository.save(role);
        return mapToRoleResponse(role);
    }

    @Transactional
    public void deleteRole(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("role.notFound"));

        if (role.getIsSystem()) {
            throw new BusinessException("SYSTEM_ROLE_DELETE", "role.systemRoleCannotDelete", HttpStatus.FORBIDDEN);
        }

        roleRepository.delete(role);
    }

    private RoleResponse mapToRoleResponse(Role role) {
        RoleResponse response = new RoleResponse();
        response.setId(role.getId());
        response.setName(role.getName());
        response.setCode(role.getCode());
        response.setDescription(role.getDescription());
        response.setIsSystem(role.getIsSystem());
        response.setIsActive(role.getIsActive());

        if (role.getPermissions() != null) {
            Set<PermissionResponse> permissions = role.getPermissions().stream()
                    .map(this::mapToPermissionResponse)
                    .collect(Collectors.toSet());
            response.setPermissions(permissions);
        }

        return response;
    }

    private PermissionResponse mapToPermissionResponse(Permission permission) {
        PermissionResponse response = new PermissionResponse();
        response.setId(permission.getId());
        response.setName(permission.getName());
        response.setCode(permission.getCode());
        response.setResource(permission.getResource());
        response.setAction(permission.getAction());
        response.setDescription(permission.getDescription());
        return response;
    }
}
