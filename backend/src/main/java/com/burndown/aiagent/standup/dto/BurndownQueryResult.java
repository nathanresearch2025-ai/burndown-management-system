package com.burndown.aiagent.standup.dto;

import com.burndown.entity.BurndownPoint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BurndownQueryResult {
    private Long sprintId;
    private String sprintName;
    private double totalStoryPoints;
    private List<BurndownPoint> burndownPoints;
    private double deviationFromIdeal;
    private String deviationStatus;
    private String errorMessage;
}
