package com.burndown.common.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserDTO {
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String avatarUrl;
    private Boolean isActive;
    private List<String> permissions;
    private LocalDateTime createdAt;
}
