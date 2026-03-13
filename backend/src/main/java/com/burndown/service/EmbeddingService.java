package com.burndown.service;

import com.burndown.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for generating text embeddings using external LLM API
 * Supports OpenAI-compatible embedding endpoints
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true")
public class EmbeddingService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.embedding-base-url:${ai.base-url}}")
    private String embeddingBaseUrl;

    @Value("${ai.embedding-api-key:${ai.api-key}}")
    private String embeddingApiKey;

    @Value("${ai.embedding-model:text-embedding-3-small}")
    private String embeddingModel;

    @Value("${ai.timeout:30s}")
    private Duration timeout;

    public EmbeddingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
        log.info("=== EmbeddingService initialized ===");
    }

    /**
     * Generate embedding vector for given text
     * @param text Input text to embed
     * @return PGvector containing the embedding
     */
    public PGvector generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            throw new BusinessException("INVALID_INPUT", "embedding.invalidInput", HttpStatus.BAD_REQUEST);
        }

        try {
            log.info("Generating embedding using: {}", embeddingBaseUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(embeddingApiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", embeddingModel);
            requestBody.put("input", text);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            String url = embeddingBaseUrl + "/embeddings";
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new BusinessException("EMBEDDING_API_ERROR", "embedding.apiError", HttpStatus.SERVICE_UNAVAILABLE);
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode embeddingNode = root.path("data").get(0).path("embedding");

            if (embeddingNode == null || !embeddingNode.isArray()) {
                throw new BusinessException("EMBEDDING_PARSE_ERROR", "embedding.parseError", HttpStatus.INTERNAL_SERVER_ERROR);
            }

            float[] embeddingArray = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                embeddingArray[i] = (float) embeddingNode.get(i).asDouble();
            }

            return new PGvector(embeddingArray);

        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to generate embedding", ex);
            throw new BusinessException("EMBEDDING_GENERATION_FAILED", "embedding.generationFailed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Generate embedding from task metadata
     * Combines title, description, type, and priority into a single text representation
     */
    public String buildTaskEmbeddingText(String title, String description, String type, String priority) {
        StringBuilder builder = new StringBuilder();

        if (title != null && !title.isBlank()) {
            builder.append("Title: ").append(title).append("\n");
        }

        if (description != null && !description.isBlank()) {
            builder.append("Description: ").append(description).append("\n");
        }

        if (type != null && !type.isBlank()) {
            builder.append("Type: ").append(type).append("\n");
        }

        if (priority != null && !priority.isBlank()) {
            builder.append("Priority: ").append(priority);
        }

        return builder.toString().trim();
    }
}
