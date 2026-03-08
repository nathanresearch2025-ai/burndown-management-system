package com.burndown.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateTaskDescriptionResponse {
    private String description;
    private List<SimilarTaskDto> similarTasks;
    private String generatedBy;
    private LocalDateTime generatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimilarTaskDto {
        private Long id;
        private String taskKey;
        private String title;
        private Double similarity;
    }
}
