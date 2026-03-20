package com.burndown.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.Set;

@Data
public class AssignRolesRequest {
    @NotEmpty(message = "Role ID list must not be empty")
    private Set<Long> roleIds;
}
