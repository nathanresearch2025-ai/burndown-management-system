package com.burndown.dto;

import lombok.Data;

@Data
public class PermissionResponse {
    private Long id;
    private String name;
    private String code;
    private String resource;
    private String action;
    private String description;
}
