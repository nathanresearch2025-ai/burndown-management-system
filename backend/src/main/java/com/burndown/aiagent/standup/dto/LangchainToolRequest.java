package com.burndown.aiagent.standup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LangchainToolRequest {
    private Long projectId;  // project ID
    private Long sprintId;   // Sprint ID
    private Long userId;     // user ID
}
