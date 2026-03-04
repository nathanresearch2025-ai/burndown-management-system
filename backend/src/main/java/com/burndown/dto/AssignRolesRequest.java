package com.burndown.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.Set;

@Data
public class AssignRolesRequest {
    @NotEmpty(message = "角色ID列表不能为空")
    private Set<Long> roleIds;
}
