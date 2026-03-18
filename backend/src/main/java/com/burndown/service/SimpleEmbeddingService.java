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

    private static final int VECTOR_DIMENSION = 384; // 与 DJL 模型维度一致
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

            // 1. 分词
            List<String> tokens = tokenize(text);

            // 2. 计算词频
            Map<String, Integer> termFrequency = calculateTermFrequency(tokens);

            // 3. 生成向量
            float[] embedding = generateVector(termFrequency, text);

            // 4. 归一化
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
     * 分词 - 支持中英文
     */
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();

        // 转小写
        text = text.toLowerCase();

        // 分割英文单词和中文字符
        StringBuilder currentToken = new StringBuilder();

        for (char c : text.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                currentToken.append(c);
            } else if (isChinese(c)) {
                // 中文字符单独作为一个 token
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken = new StringBuilder();
                }
                tokens.add(String.valueOf(c));
            } else {
                // 分隔符
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
     * 判断是否为中文字符
     */
    private boolean isChinese(char c) {
        return c >= 0x4E00 && c <= 0x9FA5;
    }

    /**
     * 计算词频
     */
    private Map<String, Integer> calculateTermFrequency(List<String> tokens) {
        Map<String, Integer> tf = new HashMap<>();
        for (String token : tokens) {
            if (token.length() >= 2) { // 过滤太短的词
                tf.put(token, tf.getOrDefault(token, 0) + 1);
            }
        }
        return tf;
    }

    /**
     * 生成向量
     */
    private float[] generateVector(Map<String, Integer> termFrequency, String originalText) {
        float[] vector = new float[VECTOR_DIMENSION];

        // 使用哈希函数将词映射到向量维度
        for (Map.Entry<String, Integer> entry : termFrequency.entrySet()) {
            String term = entry.getKey();
            int frequency = entry.getValue();

            // 使用多个哈希函数增加分布均匀性
            int[] indices = hashTerm(term);

            for (int index : indices) {
                // TF-IDF 权重（简化版）
                float weight = (float) (Math.log(1 + frequency) * 1.5);
                vector[index] += weight;
            }
        }

        // 添加文本长度特征
        float lengthFeature = (float) Math.log(1 + originalText.length()) / 10;
        vector[0] += lengthFeature;

        return vector;
    }

    /**
     * 使用多个哈希函数映射词到向量索引
     */
    private int[] hashTerm(String term) {
        int[] indices = new int[3]; // 使用3个哈希函数

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(term.getBytes(StandardCharsets.UTF_8));

            // 从哈希值中提取3个索引
            for (int i = 0; i < 3; i++) {
                int value = Math.abs(hash[i * 4] << 24 |
                                    (hash[i * 4 + 1] & 0xFF) << 16 |
                                    (hash[i * 4 + 2] & 0xFF) << 8 |
                                    (hash[i * 4 + 3] & 0xFF));
                indices[i] = value % VECTOR_DIMENSION;
            }
        } catch (Exception e) {
            // 降级到简单哈希
            indices[0] = Math.abs(term.hashCode()) % VECTOR_DIMENSION;
            indices[1] = Math.abs((term + "salt1").hashCode()) % VECTOR_DIMENSION;
            indices[2] = Math.abs((term + "salt2").hashCode()) % VECTOR_DIMENSION;
        }

        return indices;
    }

    /**
     * L2 归一化
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
