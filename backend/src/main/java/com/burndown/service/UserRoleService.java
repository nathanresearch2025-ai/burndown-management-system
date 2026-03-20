package com.burndown.service;

import com.burndown.dto.AssignRolesRequest;
import com.burndown.dto.PermissionResponse;
import com.burndown.dto.RoleResponse;
import com.burndown.entity.Permission;
import com.burndown.entity.Role;
import com.burndown.entity.UserRole;
import com.burndown.exception.ResourceNotFoundException;
import com.burndown.repository.RoleRepository;
import com.burndown.repository.UserRepository;
import com.burndown.repository.UserRoleRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserRoleService {

    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    public UserRoleService(UserRoleRepository userRoleRepository,
                          RoleRepository roleRepository,
                          UserRepository userRepository) {
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    public List<RoleResponse> getUserRoles(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("user.notFound");
        }

        List<Long> roleIds = userRoleRepository.findRoleIdsByUserId(userId);
        return roleRepository.findAllById(roleIds).stream()
                .map(this::mapToRoleResponse)
                .collect(Collectors.toList());
    }

    public Set<PermissionResponse> getUserPermissions(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("user.notFound");
        }

        // Optimization: fetch all permissions in a single query to avoid N+1 queries.
        List<String> permissionCodes = userRoleRepository.findPermissionCodesByUserId(userId);

        // If full PermissionResponse objects are needed, a second query can be made here.
        // For most scenarios, permission codes alone are sufficient.
        List<Long> roleIds = userRoleRepository.findRoleIdsByUserId(userId);
        List<Role> roles = roleRepository.findAllById(roleIds);

        Set<Permission> allPermissions = new HashSet<>();
        for (Role role : roles) {
            if (role.getIsActive()) {
                allPermissions.addAll(role.getPermissions());
            }
        }

        return allPermissions.stream()
                .map(this::mapToPermissionResponse)
                .collect(Collectors.toSet());
    }

    // Optimized permission-code query method (used for JWT generation).
    // @Cacheable(value = "permissions", key = "'user:' + #userId")  // temporarily disabled to avoid Set/ArrayList type-conversion issues
    public Set<String> getUserPermissionCodes(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("user.notFound");
        }
        // Convert the query result to a Set to avoid type-conversion exceptions.
        return new HashSet<>(userRoleRepository.findPermissionCodesByUserId(userId));
    }

    @Transactional
    @CacheEvict(value = "permissions", key = "#userId")
    public void assignRoles(Long userId, AssignRolesRequest request, Long assignedBy) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("user.notFound");
        }

        // Remove existing roles.
        userRoleRepository.findByUserId(userId).forEach(userRoleRepository::delete);

        // Assign new roles.
        for (Long roleId : request.getRoleIds()) {
            if (!roleRepository.existsById(roleId)) {
                throw new ResourceNotFoundException("role.notFound");
            }

            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRole.setAssignedBy(assignedBy);
            userRoleRepository.save(userRole);
        }
    }

    @Transactional
    public void assignRoleToUser(Long userId, Long roleId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("user.notFound");
        }
        if (!roleRepository.existsById(roleId)) {
            throw new ResourceNotFoundException("role.notFound");
        }

        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        userRole.setAssignedBy(userId); // self-registration: the assigner is the user themselves
        userRoleRepository.save(userRole);
    }

    @Transactional
    public void removeRole(Long userId, Long roleId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("user.notFound");
        }
        if (!roleRepository.existsById(roleId)) {
            throw new ResourceNotFoundException("role.notFound");
        }

        userRoleRepository.deleteByUserIdAndRoleId(userId, roleId);
    }

    private RoleResponse mapToRoleResponse(Role role) {
        RoleResponse response = new RoleResponse();
        response.setId(role.getId());
        response.setName(role.getName());
        response.setCode(role.getCode());
        response.setDescription(role.getDescription());
        response.setIsSystem(role.getIsSystem());
        response.setIsActive(role.getIsActive());
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
