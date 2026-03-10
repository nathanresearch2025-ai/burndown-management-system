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

        // 优化：使用单次查询获取所有权限，避免N+1查询
        List<String> permissionCodes = userRoleRepository.findPermissionCodesByUserId(userId);

        // 如果需要返回完整的PermissionResponse，可以再查询一次
        // 但对于大多数场景，只需要权限代码即可
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

    // 新增：优化的权限代码查询方法（用于JWT生成）
    // @Cacheable(value = "permissions", key = "'user:' + #userId")  // 暂时禁用缓存以避免 Set/ArrayList 类型转换问题
    public Set<String> getUserPermissionCodes(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("user.notFound");
        }
        // 将查询结果转换为 Set，避免类型转换异常
        return new HashSet<>(userRoleRepository.findPermissionCodesByUserId(userId));
    }

    @Transactional
    @CacheEvict(value = "permissions", key = "#userId")
    public void assignRoles(Long userId, AssignRolesRequest request, Long assignedBy) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("user.notFound");
        }

        // 删除现有角色
        userRoleRepository.findByUserId(userId).forEach(userRoleRepository::delete);

        // 分配新角色
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
        userRole.setAssignedBy(userId); // 自注册时，分配者为自己
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
