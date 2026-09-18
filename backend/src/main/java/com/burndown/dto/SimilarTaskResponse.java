package com.burndown.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimilarTaskResponse {
    private Long id;
    private String taskKey;
    private String title;
    private String description;
    private String type;
    private String status;
    private String priority;
    private BigDecimal storyPoints;
    private Double similarityScore;
}
