package com.burndown.dto;

import lombok.Data;

@Data
public class SimilarTaskSearchRequest {
    private Long projectId;
    private String title;
    private String description;
    private String type;
    private String priority;
    private Integer limit = 5;
}
