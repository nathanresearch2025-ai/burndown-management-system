package com.burndown.service;

import com.burndown.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    }

    public String generateTaskDescription(String prompt) {
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
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiBaseUrl))
                    .timeout(aiTimeout)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .header("Authorization", "Bearer " + aiApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
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
