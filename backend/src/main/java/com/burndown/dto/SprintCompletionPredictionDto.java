package com.burndown.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for Sprint completion prediction results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SprintCompletionPredictionDto {

    /**
     * Completion probability (0.0 – 1.0).
     */
    @JsonProperty("probability")
    private Double probability;

    /**
     * Risk level: GREEN, YELLOW, or RED.
     */
    @JsonProperty("riskLevel")
    private String riskLevel;

    /**
     * Summary of input features used for the prediction.
     */
    @JsonProperty("featureSummary")
    private FeatureSummary featureSummary;

    /**
     * Unix timestamp (ms) of when the prediction was generated.
     */
    @JsonProperty("predictedAt")
    private Long predictedAt;

    /**
     * Inner class holding the feature summary.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureSummary {

        @JsonProperty("daysElapsedRatio")
        private Double daysElapsedRatio;

        @JsonProperty("remainingRatio")
        private Double remainingRatio;

        @JsonProperty("velocityCurrent")
        private Double velocityCurrent;

        @JsonProperty("velocityAvg")
        private Double velocityAvg;

        @JsonProperty("projectedCompletionRatio")
        private Double projectedCompletionRatio;

        @JsonProperty("blockedStories")
        private Integer blockedStories;

        @JsonProperty("attendanceRate")
        private Double attendanceRate;
    }
}