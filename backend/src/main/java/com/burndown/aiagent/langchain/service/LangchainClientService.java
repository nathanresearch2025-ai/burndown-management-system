package com.burndown.aiagent.langchain.service;

import com.burndown.aiagent.langchain.dto.LangchainStandupRequest;
import com.burndown.aiagent.langchain.dto.LangchainStandupResponse;
import com.burndown.exception.BusinessException;
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

@Slf4j
@Service
public class LangchainClientService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${langchain.enabled:false}")
    private boolean enabled;

    @Value("${langchain.base-url:}")
    private String baseUrl;

    @Value("${langchain.timeout:30s}")
    private Duration timeout;

    public LangchainClientService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().build();
    }

    public LangchainStandupResponse queryStandup(LangchainStandupRequest request) {
        if (!enabled) {
            throw new BusinessException("LANGCHAIN_DISABLED", "langchain.disabled", HttpStatus.BAD_REQUEST);
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BusinessException("LANGCHAIN_CONFIG_MISSING", "langchain.configMissing", HttpStatus.BAD_REQUEST);
        }

        try { // handles IO and interrupt exceptions
            String url = baseUrl + "/agent/standup/query"; // LangChain endpoint URL

            HttpRequest httpRequest = HttpRequest.newBuilder() // build the HTTP request
                    .uri(URI.create(url)) // set request URL
                    .timeout(timeout) // set request timeout
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                log.error("LangChain service error: {}", response.body());
                throw new BusinessException("LANGCHAIN_SERVICE_ERROR", "langchain.serviceUnavailable", HttpStatus.BAD_GATEWAY);
            }

            return objectMapper.readValue(response.body(), LangchainStandupResponse.class);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt(); // restore interrupt flag
            }
            throw new BusinessException("LANGCHAIN_SERVICE_ERROR", "langchain.serviceUnavailable", HttpStatus.BAD_GATEWAY);
        }
    }
}
