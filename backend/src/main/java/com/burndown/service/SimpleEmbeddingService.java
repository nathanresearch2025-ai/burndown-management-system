package com.burndown.service;

import com.burndown.exception.BusinessException;
import com.pgvector.PGvector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Simple local embedding service using TF-IDF-like approach
 * No external API required, fully offline
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "ai.embedding", name = "provider", havingValue = "simple")
public class SimpleEmbeddingService {

    private static final int VECTOR_DIMENSION = 384; // matches the DJL model dimension
    private static final int HASH_BUCKETS = 100;

    /**
     * Generate embedding vector using simple hashing and TF-IDF approach
     * @param text Input text to embed
     * @return PGvector containing the embedding
     */
    public PGvector generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            throw new BusinessException("INVALID_INPUT", "embedding.invalidInput", HttpStatus.BAD_REQUEST);
        }

        try {
            log.debug("Generating simple embedding for text: {}", text.substring(0, Math.min(50, text.length())));

            // 1. Tokenize.
            List<String> tokens = tokenize(text);

            // 2. Compute term frequency.
            Map<String, Integer> termFrequency = calculateTermFrequency(tokens);

            // 3. Generate vector.
            float[] embedding = generateVector(termFrequency, text);

            // 4. Normalize.
            normalize(embedding);

            log.debug("Generated simple embedding with dimension: {}", embedding.length);
            return new PGvector(embedding);

        } catch (Exception ex) {
            log.error("Failed to generate simple embedding", ex);
            throw new BusinessException("EMBEDDING_GENERATION_FAILED", "embedding.generationFailed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Build task embedding text from metadata
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
            "provider", "simple",
            "algorithm", "TF-IDF + Hashing",
            "dimension", VECTOR_DIMENSION,
            "status", "ready"
        );
    }

    /**
     * Tokenize text — supports both Chinese and English.
     */
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();

        // Convert to lower-case.
        text = text.toLowerCase();

        // Split into English words and individual Chinese characters.
        StringBuilder currentToken = new StringBuilder();

        for (char c : text.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                currentToken.append(c);
            } else if (isChinese(c)) {
                // Each Chinese character is its own token.
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken = new StringBuilder();
                }
                tokens.add(String.valueOf(c));
            } else {
                // Delimiter character — flush the current token buffer.
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken = new StringBuilder();
                }
            }
        }

        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }

        return tokens;
    }

    /**
     * Returns true if the character is in the CJK Unified Ideographs block.
     */
    private boolean isChinese(char c) {
        return c >= 0x4E00 && c <= 0x9FA5;
    }

    /**
     * Calculate term frequency for the token list.
     */
    private Map<String, Integer> calculateTermFrequency(List<String> tokens) {
        Map<String, Integer> tf = new HashMap<>();
        for (String token : tokens) {
            if (token.length() >= 2) { // skip tokens that are too short
                tf.put(token, tf.getOrDefault(token, 0) + 1);
            }
        }
        return tf;
    }

    /**
     * Generate the raw float vector from term frequencies.
     */
    private float[] generateVector(Map<String, Integer> termFrequency, String originalText) {
        float[] vector = new float[VECTOR_DIMENSION];

        // Map each term to vector dimensions using hash functions.
        for (Map.Entry<String, Integer> entry : termFrequency.entrySet()) {
            String term = entry.getKey();
            int frequency = entry.getValue();

            // Use multiple hash functions to improve distribution uniformity.
            int[] indices = hashTerm(term);

            for (int index : indices) {
                // Simplified TF-IDF weight.
                float weight = (float) (Math.log(1 + frequency) * 1.5);
                vector[index] += weight;
            }
        }

        // Add a text-length feature to the first dimension.
        float lengthFeature = (float) Math.log(1 + originalText.length()) / 10;
        vector[0] += lengthFeature;

        return vector;
    }

    /**
     * Map a term to vector indices using multiple hash functions.
     */
    private int[] hashTerm(String term) {
        int[] indices = new int[3]; // use 3 hash functions

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(term.getBytes(StandardCharsets.UTF_8));

            // Extract 3 indices from the hash bytes.
            for (int i = 0; i < 3; i++) {
                int value = Math.abs(hash[i * 4] << 24 |
                                    (hash[i * 4 + 1] & 0xFF) << 16 |
                                    (hash[i * 4 + 2] & 0xFF) << 8 |
                                    (hash[i * 4 + 3] & 0xFF));
                indices[i] = value % VECTOR_DIMENSION;
            }
        } catch (Exception e) {
            // Fall back to simple hashing.
            indices[0] = Math.abs(term.hashCode()) % VECTOR_DIMENSION;
            indices[1] = Math.abs((term + "salt1").hashCode()) % VECTOR_DIMENSION;
            indices[2] = Math.abs((term + "salt2").hashCode()) % VECTOR_DIMENSION;
        }

        return indices;
    }

    /**
     * L2 normalization.
     */
    private void normalize(float[] vector) {
        double sum = 0;
        for (float v : vector) {
            sum += v * v;
        }

        if (sum > 0) {
            double norm = Math.sqrt(sum);
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
    }
}
