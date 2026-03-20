package com.burndown.aiagent.langchain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LangchainStandupRequest {
    private String question;  // user question
    private Long projectId;   // project ID
    private Long sprintId;    // Sprint ID
    private Long userId;      // user ID
    private String timezone;  // timezone
    private String traceId;   // distributed trace ID
}
