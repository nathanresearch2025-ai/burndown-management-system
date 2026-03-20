package com.burndown.aiagent.langchain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LangchainStandupResponse {
    private String answer;           // final answer
    private List<String> toolsUsed;  // tools that were invoked
    private List<String> evidence;   // evidence list
    private String riskLevel;        // risk level
}
