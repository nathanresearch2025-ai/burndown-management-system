package com.burndown.dto;

import lombok.Data;
import java.util.Set;

@Data
public class RoleResponse {
    private Long id;
    private String name;
    private String code;
    private String description;
    private Boolean isSystem;
    private Boolean isActive;
    private Set<PermissionResponse> permissions;
}
