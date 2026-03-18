package com.burndown.service;

import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.TranslateException;
import com.burndown.exception.BusinessException;
import com.pgvector.PGvector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;

/**
 * DJL-based local embedding service using HuggingFace models
 * Provides offline vector generation without external API calls
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "ai.embedding", name = "provider", havingValue = "djl")
public class DjlEmbeddingService {

    @Value("${ai.embedding.djl.model-name:sentence-transformers/all-MiniLM-L6-v2}")
    private String modelName;

    @Value("${ai.embedding.djl.hf-token:}")
    private String huggingFaceToken;

    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;

    @PostConstruct
    public void initialize() {
        try {
            log.info("Initializing DJL Embedding Service with model: {}", modelName);

            Criteria.Builder<String, float[]> builder = Criteria.builder()
                    .setTypes(String.class, float[].class)
                    .optModelUrls("djl://ai.djl.huggingface.pytorch/" + modelName)
                    .optEngine("PyTorch")
                    .optProgress(new ProgressBar())
                    .optApplication(Application.NLP.TEXT_EMBEDDING);

            // Add HuggingFace token if provided
            if (huggingFaceToken != null && !huggingFaceToken.isBlank()) {
                builder.optOption("hf_token", huggingFaceToken);
            }

            Criteria<String, float[]> criteria = builder.build();

            model = criteria.loadModel();
            predictor = model.newPredictor();

            log.info("DJL Embedding Service initialized successfully");

        } catch (ModelNotFoundException | MalformedModelException | IOException ex) {
            log.error("Failed to load DJL model: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to initialize DJL embedding model", ex);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (predictor != null) {
            predictor.close();
        }
        if (model != null) {
            model.close();
        }
        log.info("DJL Embedding Service cleaned up");
    }

    /**
     * Generate embedding vector for given text using local DJL model
     * @param text Input text to embed
     * @return PGvector containing the embedding
     */
    public PGvector generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            throw new BusinessException("INVALID_INPUT", "embedding.invalidInput", HttpStatus.BAD_REQUEST);
        }

        try {
            log.debug("Generating embedding for text: {}", text.substring(0, Math.min(50, text.length())));

            float[] embedding = predictor.predict(text);

            if (embedding == null || embedding.length == 0) {
                throw new BusinessException("EMBEDDING_GENERATION_FAILED", "embedding.generationFailed", HttpStatus.INTERNAL_SERVER_ERROR);
            }

            log.debug("Generated embedding with dimension: {}", embedding.length);
            return new PGvector(embedding);

        } catch (TranslateException ex) {
            log.error("Failed to generate embedding with DJL", ex);
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

    /**
     * Get model information
     */
    public Map<String, Object> getModelInfo() {
        return Map.of(
            "provider", "djl",
            "modelName", modelName,
            "engine", "PyTorch",
            "status", model != null ? "loaded" : "not_loaded"
        );
    }
}
