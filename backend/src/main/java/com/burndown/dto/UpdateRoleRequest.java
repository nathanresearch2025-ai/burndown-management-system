package com.burndown.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.Set;

@Data
public class UpdateRoleRequest {
    private String name;
    private String description;
    private Boolean isActive;
    private Set<Long> permissionIds;
}
