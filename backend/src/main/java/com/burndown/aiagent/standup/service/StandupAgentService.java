package com.burndown.aiagent.standup.service;

import com.burndown.aiagent.standup.config.StandupAgentMetrics;
import com.burndown.aiagent.standup.dto.StandupQueryRequest;
import com.burndown.aiagent.standup.dto.StandupQueryResponse;
import com.burndown.aiagent.standup.entity.AgentChatMessage;
import com.burndown.aiagent.standup.entity.AgentChatSession;
import com.burndown.aiagent.standup.prompt.StandupPromptTemplate;
import com.burndown.aiagent.standup.repository.AgentChatMessageRepository;
import com.burndown.aiagent.standup.repository.AgentChatSessionRepository;
import com.burndown.aiagent.standup.tool.StandupBurndownTools;
import com.burndown.aiagent.standup.tool.StandupRiskTools;
import com.burndown.aiagent.standup.tool.StandupTaskTools;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StandupAgentService {

    private final ChatClient.Builder chatClientBuilder;
    private final AgentChatSessionRepository sessionRepository;
    private final AgentChatMessageRepository messageRepository;
    private final ObjectMapper objectMapper;
    private final StandupTaskTools taskTools;
    private final StandupBurndownTools burndownTools;
    private final StandupRiskTools riskTools;
    private final StandupAgentMetrics metrics;

    @Transactional
    public StandupQueryResponse query(StandupQueryRequest request, Long userId, String traceId) {
        long startTime = System.currentTimeMillis();
        metrics.getRequestsTotal().increment();

        try {
            // Create or get session
            String sessionKey = generateSessionKey(userId, request.getProjectId());
            AgentChatSession session = getOrCreateSession(sessionKey, userId, request.getProjectId());

            // Build user prompt
            String userPrompt = buildUserPrompt(request);

            // Call LLM with tools - use function names instead of method references
            ChatClient chatClient = chatClientBuilder.build();

            ChatResponse response = metrics.getDurationTimer().record(() ->
                    chatClient.prompt()
                            .system(StandupPromptTemplate.SYSTEM_PROMPT)
                            .user(userPrompt)
                            .functions("getInProgressTasks", "getSprintBurndown", "evaluateBurndownRisk")
                            .call()
                            .chatResponse()
            );

            String answer = response.getResult().getOutput().getContent();

            // Parse response and build result
            StandupQueryResponse queryResponse = parseResponse(answer);

            // Save message
            long latency = System.currentTimeMillis() - startTime;
            saveMessage(session.getId(), request.getQuestion(), answer, queryResponse, traceId, (int) latency);

            return queryResponse;

        } catch (Exception e) {
            log.error("Error processing standup query: {}", e.getMessage(), e);
            metrics.getFallbackTotal().increment();
            throw new RuntimeException("Failed to process query: " + e.getMessage(), e);
        }
    }

    private String generateSessionKey(Long userId, Long projectId) {
        return String.format("standup_%d_%d_%s", userId, projectId, UUID.randomUUID().toString().substring(0, 8));
    }

    private AgentChatSession getOrCreateSession(String sessionKey, Long userId, Long projectId) {
        return sessionRepository.findBySessionKey(sessionKey)
                .orElseGet(() -> {
                    AgentChatSession session = AgentChatSession.builder()
                            .sessionKey(sessionKey)
                            .userId(userId)
                            .projectId(projectId)
                            .build();
                    return sessionRepository.save(session);
                });
    }

    private String buildUserPrompt(StandupQueryRequest request) {
        return StandupPromptTemplate.USER_PROMPT_TEMPLATE
                .replace("{question}", request.getQuestion())
                .replace("{projectId}", String.valueOf(request.getProjectId()))
                .replace("{sprintId}", request.getSprintId() != null ? String.valueOf(request.getSprintId()) : "N/A")
                .replace("{timezone}", request.getTimezone());
    }

    private StandupQueryResponse parseResponse(String answer) {
        // Simple parsing - in production, you might want more sophisticated parsing
        return StandupQueryResponse.builder()
                .answer(answer)
                .summary(StandupQueryResponse.StandupSummary.builder().build())
                .toolsUsed(new ArrayList<>())
                .evidence(new ArrayList<>())
                .build();
    }

    private void saveMessage(Long sessionId, String question, String answer,
                           StandupQueryResponse response, String traceId, int latencyMs) {
        try {
            String toolsUsedJson = objectMapper.writeValueAsString(response.getToolsUsed());

            AgentChatMessage message = AgentChatMessage.builder()
                    .sessionId(sessionId)
                    .role("assistant")
                    .question(question)
                    .answer(answer)
                    .toolsUsed(toolsUsedJson)
                    .riskLevel(response.getSummary() != null ? response.getSummary().getRiskLevel() : null)
                    .traceId(traceId)
                    .latencyMs(latencyMs)
                    .build();

            messageRepository.save(message);
        } catch (JsonProcessingException e) {
            log.error("Failed to save message: {}", e.getMessage(), e);
        }
    }
}
