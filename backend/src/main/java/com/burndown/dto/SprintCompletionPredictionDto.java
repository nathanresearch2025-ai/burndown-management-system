package com.burndown.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Sprint 完成预测结果 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SprintCompletionPredictionDto {

    /**
     * 完成概率 (0.0 - 1.0)
     */
    @JsonProperty("probability")
    private Double probability;

    /**
     * 风险等级: GREEN, YELLOW, RED
     */
    @JsonProperty("riskLevel")
    private String riskLevel;

    /**
     * 特征摘要
     */
    @JsonProperty("featureSummary")
    private FeatureSummary featureSummary;

    /**
     * 预测时间戳
     */
    @JsonProperty("predictedAt")
    private Long predictedAt;

    /**
     * 特征摘要内部类
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