package com.burndown.service;

import com.pgvector.PGvector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Unified embedding service that delegates to either DJL (local), Simple (local), or API-based embedding
 * Provides a single interface for embedding generation regardless of the underlying provider
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true")
public class UnifiedEmbeddingService {

    @Value("${ai.embedding.provider:simple}")
    private String embeddingProvider;

    private final EmbeddingService apiEmbeddingService;
    private final DjlEmbeddingService djlEmbeddingService;
    private final SimpleEmbeddingService simpleEmbeddingService;

    public UnifiedEmbeddingService(
            @Autowired(required = false) EmbeddingService apiEmbeddingService,
            @Autowired(required = false) DjlEmbeddingService djlEmbeddingService,
            @Autowired(required = false) SimpleEmbeddingService simpleEmbeddingService) {
        this.apiEmbeddingService = apiEmbeddingService;
        this.djlEmbeddingService = djlEmbeddingService;
        this.simpleEmbeddingService = simpleEmbeddingService;
    }

    /**
     * Generate embedding vector using the configured provider
     * @param text Input text to embed
     * @return PGvector containing the embedding
     */
    public PGvector generateEmbedding(String text) {
        if ("djl".equalsIgnoreCase(embeddingProvider)) {
            if (djlEmbeddingService == null) {
                log.warn("DJL embedding service not available, falling back to simple");
                return simpleEmbeddingService != null ? simpleEmbeddingService.generateEmbedding(text) : generateFallback(text);
            }
            log.debug("Using DJL local embedding");
            return djlEmbeddingService.generateEmbedding(text);
        } else if ("simple".equalsIgnoreCase(embeddingProvider)) {
            if (simpleEmbeddingService == null) {
                log.error("Simple embedding service not available");
                throw new IllegalStateException("Simple embedding service not available");
            }
            log.debug("Using Simple local embedding");
            return simpleEmbeddingService.generateEmbedding(text);
        } else {
            if (apiEmbeddingService == null) {
                log.warn("API embedding service not available, falling back to simple");
                return simpleEmbeddingService != null ? simpleEmbeddingService.generateEmbedding(text) : generateFallback(text);
            }
            log.debug("Using API-based embedding");
            return apiEmbeddingService.generateEmbedding(text);
        }
    }

    /**
     * Build task embedding text from metadata
     */
    public String buildTaskEmbeddingText(String title, String description, String type, String priority) {
        // All services use the same text building logic
        if ("djl".equalsIgnoreCase(embeddingProvider) && djlEmbeddingService != null) {
            return djlEmbeddingService.buildTaskEmbeddingText(title, description, type, priority);
        } else if ("simple".equalsIgnoreCase(embeddingProvider) && simpleEmbeddingService != null) {
            return simpleEmbeddingService.buildTaskEmbeddingText(title, description, type, priority);
        } else if (apiEmbeddingService != null) {
            return apiEmbeddingService.buildTaskEmbeddingText(title, description, type, priority);
        }

        // Fallback implementation
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

    /**
     * Get current provider information
     */
    public Map<String, Object> getProviderInfo() {
        if ("djl".equalsIgnoreCase(embeddingProvider) && djlEmbeddingService != null) {
            return djlEmbeddingService.getModelInfo();
        } else if ("simple".equalsIgnoreCase(embeddingProvider) && simpleEmbeddingService != null) {
            return simpleEmbeddingService.getModelInfo();
        } else {
            return Map.of(
                "provider", "api",
                "status", apiEmbeddingService != null ? "available" : "unavailable"
            );
        }
    }

    /**
     * Fallback: generate a simple random-like vector based on text hash
     */
    private PGvector generateFallback(String text) {
        log.warn("Using fallback embedding generation");
        float[] vector = new float[384];
        int hash = text.hashCode();
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) Math.sin(hash + i) * 0.1f;
        }
        return new PGvector(vector);
    }
}
