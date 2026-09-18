package com.burndown.auth.dto;

import lombok.Data;

import java.util.List;

@Data
public class LoginResponse {
    private String token;
    private String tokenType = "Bearer";
    private Long expiresIn;
    private Long userId;
    private String username;
    private String email;
    private String fullName;
    private List<String> permissions;
}
