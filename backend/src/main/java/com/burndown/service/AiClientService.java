package com.burndown.service;

import com.burndown.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiClientService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${ai.enabled:false}")
    private boolean aiEnabled;

    @Value("${ai.base-url:}")
    private String aiBaseUrl;

    @Value("${ai.api-key:}")
    private String aiApiKey;

    @Value("${ai.chat-model:}")
    private String aiChatModel;

    @Value("${ai.timeout:30s}")
    private Duration aiTimeout;

    public AiClientService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().build();
        log.info("=== AiClientService initialized ===");
    }

    public String generateTaskDescription(String prompt) {
        log.info("=== generateTaskDescription called ===");
        log.info("AI Enabled: {}", aiEnabled);
        log.info("AI Base URL: {}", aiBaseUrl);
        log.info("AI Model: {}", aiChatModel);

        if (!aiEnabled) {
            throw new BusinessException("AI_DISABLED", "task.ai.disabled", HttpStatus.BAD_REQUEST);
        }
        if (aiBaseUrl == null || aiBaseUrl.isBlank() || aiApiKey == null || aiApiKey.isBlank() || aiChatModel == null || aiChatModel.isBlank()) {
            throw new BusinessException("AI_CONFIG_MISSING", "task.ai.configMissing", HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> requestBody = Map.of(
                "model", aiChatModel,
                "messages", List.of(
                        Map.of("role", "system", "content", "You generate concise, implementation-oriented task descriptions for Scrum teams. Return plain text only."),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.4
        );

        try {
            String chatUrl = aiBaseUrl + "/chat/completions";
            log.info("Calling DeepSeek API: {}", chatUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(chatUrl))
                    .timeout(aiTimeout)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .header("Authorization", "Bearer " + aiApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("DeepSeek API response status: {}", response.statusCode());

            if (response.statusCode() >= 400) {
                log.error("DeepSeek API error: {}", response.body());
                throw new BusinessException("AI_SERVICE_ERROR", "task.ai.serviceUnavailable", HttpStatus.BAD_GATEWAY);
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
                throw new BusinessException("AI_EMPTY_RESPONSE", "task.ai.emptyResponse", HttpStatus.BAD_GATEWAY);
            }
            return contentNode.asText().trim();
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException("AI_SERVICE_ERROR", "task.ai.serviceUnavailable", HttpStatus.BAD_GATEWAY);
        }
    }
}
