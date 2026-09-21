package com.burndown.aiagent.standup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StandupQueryResponse {
    private String answer;
    private StandupSummary summary;
    private List<String> toolsUsed;
    private List<String> evidence;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StandupSummary {
        private Integer inProgressCount;
        private Double burndownDeviationHours;
        private String riskLevel;
        private Map<String, Object> additionalInfo;
    }
}
