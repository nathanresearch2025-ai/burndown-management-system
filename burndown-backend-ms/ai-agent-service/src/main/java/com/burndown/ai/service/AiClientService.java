package com.burndown.ai.service;

import com.burndown.ai.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiClientService {

    private final AiProperties aiProperties;

    public String chat(List<Map<String, String>> messages) {
        if (!aiProperties.isEnabled()) {
            return "AI功能未启用，请配置 ai.enabled=true 及相关参数。";
        }
        try {
            RestClient client = RestClient.builder()
                    .baseUrl(aiProperties.getBaseUrl())
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.getApiKey())
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();

            Map<String, Object> requestBody = Map.of(
                    "model", aiProperties.getChatModel(),
                    "messages", messages,
                    "max_tokens", aiProperties.getMaxTokens()
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("choices")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    return (String) message.get("content");
                }
            }
            return "AI返回结果解析失败";
        } catch (Exception e) {
            log.error("AI API call failed: {}", e.getMessage(), e);
            return "AI服务暂时不可用: " + e.getMessage();
        }
    }
}
