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

/**
 * Core service class for the Standup Agent.
 *
 * Responsibilities:
 * 1. Handle intelligent Q&A requests for Scrum daily standups.
 * 2. Integrate with Spring AI framework to invoke large language models (LLMs).
 * 3. Manage session context to support multi-turn conversations.
 * 4. Provide tool-calling capability (Function Calling) so the AI can query tasks, burndown data, and risk assessments.
 * 5. Record conversation history and performance metrics.
 *
 * Workflow:
 * User question -> create/fetch session -> build prompt -> call LLM (with tools) -> parse response -> save record -> return result.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StandupAgentService {

    // Spring AI chat client builder, used to interact with the LLM.
    private final ChatClient.Builder chatClientBuilder;

    // Session repository, manages user conversation sessions.
    private final AgentChatSessionRepository sessionRepository;

    // Message repository, stores each round of Q&A records.
    private final AgentChatMessageRepository messageRepository;

    // JSON serialization utility.
    private final ObjectMapper objectMapper;

    // Task tool: provides the ability to query task-related data.
    private final StandupTaskTools taskTools;

    // Burndown tool: provides the ability to query Sprint burndown chart data.
    private final StandupBurndownTools burndownTools;

    // Risk assessment tool: provides the ability to evaluate project risk.
    private final StandupRiskTools riskTools;

    // Performance monitoring metrics collector.
    private final StandupAgentMetrics metrics;

    /**
     * Core method for processing standup Q&A requests.
     *
     * @param request the standup Q&A request, containing the question, project ID, Sprint ID, etc.
     * @param userId the current user ID
     * @param traceId request trace ID used for log correlation and troubleshooting
     * @return standup Q&A response containing the AI-generated answer, summary, tools used, etc.
     *
     * Execution flow:
     * 1. Record request start time and increment the request count metric.
     * 2. Create or retrieve the user's conversation session (supports context memory).
     * 3. Build the user prompt (fill request parameters into the template).
     * 4. Call the LLM with registered tool functions (Function Calling).
     * 5. Parse the LLM response.
     * 6. Save the conversation record to the database.
     * 7. Return the response result.
     */
    @Transactional
    public StandupQueryResponse query(StandupQueryRequest request, Long userId, String traceId) {
        long startTime = System.currentTimeMillis();
        // Increment total request count metric.
        metrics.getRequestsTotal().increment();

        try {
            // Step 1: Create or retrieve the session.
            // Session key format: standup_{userId}_{projectId}_{random 8-char UUID}
            String sessionKey = generateSessionKey(userId, request.getProjectId());
            AgentChatSession session = getOrCreateSession(sessionKey, userId, request.getProjectId());

            // Step 2: Build the user prompt.
            // Fill question, project ID, Sprint ID, timezone, etc. from the request into the prompt template.
            String userPrompt = buildUserPrompt(request);

            // Step 3: Call the LLM (large language model).
            ChatClient chatClient = chatClientBuilder.build();

            // Use Spring AI's Function Calling mechanism.
            // The LLM can automatically decide whether to call these tool functions to fetch data based on the user question.
            // Registered tool functions:
            // - getInProgressTasks: retrieve in-progress tasks
            // - getSprintBurndown: retrieve Sprint burndown chart data
            // - evaluateBurndownRisk: assess burndown risk
            ChatResponse response = metrics.getDurationTimer().record(() ->
                    chatClient.prompt()
                            .system(StandupPromptTemplate.SYSTEM_PROMPT)  // system prompt: defines the AI's role and behavior
                            .user(userPrompt)  // user prompt: the user's specific question
                            .functions("getInProgressTasks", "getSprintBurndown", "evaluateBurndownRisk")  // register tool functions
                            .call()
                            .chatResponse()
            );

            // Step 4: Extract the LLM's answer content.
            String answer = response.getResult().getOutput().getContent();

            // Step 5: Parse the response and build a structured return object.
            StandupQueryResponse queryResponse = parseResponse(answer);

            // Step 6: Save the conversation record to the database.
            long latency = System.currentTimeMillis() - startTime;
            saveMessage(session.getId(), request.getQuestion(), answer, queryResponse, traceId, (int) latency);

            return queryResponse;

        } catch (Exception e) {
            log.error("Error processing standup query: {}", e.getMessage(), e);
            // Increment failure count metric.
            metrics.getFallbackTotal().increment();
            throw new RuntimeException("Failed to process query: " + e.getMessage(), e);
        }
    }

    /**
     * Generate a unique session key.
     *
     * Format: standup_{userId}_{projectId}_{8-char random UUID}
     * Purpose: identifies a user's conversation session within a specific project.
     */
    private String generateSessionKey(Long userId, Long projectId) {
        return String.format("standup_%d_%d_%s", userId, projectId, UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * Get or create a conversation session.
     *
     * If the session already exists, reuse it (supports context memory).
     * If the session does not exist, create a new one and save it to the database.
     */
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

    /**
     * Build the user prompt.
     *
     * Fills request parameters into the prompt template:
     * - {question}: the user's question
     * - {projectId}: project ID
     * - {sprintId}: Sprint ID (optional)
     * - {timezone}: timezone information
     */
    private String buildUserPrompt(StandupQueryRequest request) {
        return StandupPromptTemplate.USER_PROMPT_TEMPLATE
                .replace("{question}", request.getQuestion())
                .replace("{projectId}", String.valueOf(request.getProjectId()))
                .replace("{sprintId}", request.getSprintId() != null ? String.valueOf(request.getSprintId()) : "N/A")
                .replace("{timezone}", request.getTimezone());
    }

    /**
     * Parse the LLM response.
     *
     * Current implementation: simple parsing — returns the AI answer text directly.
     * TODO: A more sophisticated parsing strategy could be implemented for production, e.g.:
     * - Extract structured information (task lists, risk levels, etc.)
     * - Identify which tools the AI used
     * - Extract key evidence and data sources
     */
    private StandupQueryResponse parseResponse(String answer) {
        // Simple parsing - in production, you might want more sophisticated parsing
        return StandupQueryResponse.builder()
                .answer(answer)
                .summary(StandupQueryResponse.StandupSummary.builder().build())
                .toolsUsed(new ArrayList<>())
                .evidence(new ArrayList<>())
                .build();
    }

    /**
     * Save a conversation message to the database.
     *
     * Recorded fields include:
     * - User question and AI answer
     * - List of tools used (JSON format)
     * - Risk level
     * - Request trace ID
     * - Response latency (milliseconds)
     *
     * Uses:
     * - Conversation history lookup
     * - Performance analysis
     * - Troubleshooting
     * - User behavior analysis
     */
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
