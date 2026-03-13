package com.burndown.service;

import com.burndown.dto.PermissionResponse;
import com.burndown.entity.Permission;
import com.burndown.repository.PermissionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    public List<PermissionResponse> getAllPermissions() {
        return permissionRepository.findAll().stream()
                .map(this::mapToPermissionResponse)
                .collect(Collectors.toList());
    }

    public List<PermissionResponse> getPermissionsByResource(String resource) {
        return permissionRepository.findByResource(resource).stream()
                .map(this::mapToPermissionResponse)
                .collect(Collectors.toList());
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
