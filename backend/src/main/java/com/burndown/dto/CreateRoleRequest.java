package com.burndown.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.Set;

@Data
public class CreateRoleRequest {
    @NotBlank(message = "Role name must not be blank")
    private String name;

    @NotBlank(message = "Role code must not be blank")
    private String code;

    private String description;

    @NotNull(message = "isActive must not be null")
    private Boolean isActive;

    private Set<Long> permissionIds;
}
