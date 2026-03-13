package com.burndown.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.Set;

@Data
public class CreateRoleRequest {
    @NotBlank(message = "角色名称不能为空")
    private String name;

    @NotBlank(message = "角色编码不能为空")
    private String code;

    private String description;

    @NotNull(message = "是否启用不能为空")
    private Boolean isActive;

    private Set<Long> permissionIds;
}
