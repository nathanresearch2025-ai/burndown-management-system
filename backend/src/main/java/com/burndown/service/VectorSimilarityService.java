package com.burndown.service;

import com.burndown.dto.SimilarTaskResponse;
import com.burndown.dto.SimilarTaskSearchRequest;
import com.burndown.entity.Task;
import com.burndown.repository.TaskRepository;
import com.burndown.util.PGvectorUtil;
import com.pgvector.PGvector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for vector similarity search
 */
@Slf4j
@Service
public class VectorSimilarityService {

    private final TaskRepository taskRepository;
    private final UnifiedEmbeddingService embeddingService;

    public VectorSimilarityService(
            TaskRepository taskRepository,
            @Autowired(required = false) UnifiedEmbeddingService embeddingService) {
        this.taskRepository = taskRepository;
        this.embeddingService = embeddingService;
    }

    /**
     * Find similar tasks based on vector similarity
     */
    @Transactional(readOnly = true)
    public List<SimilarTaskResponse> findSimilarTasks(SimilarTaskSearchRequest request) {
        if (embeddingService == null) {
            log.error("Embedding service not available");
            throw new IllegalStateException("Embedding service not available");
        }

        // Build embedding text
        String embeddingText = buildEmbeddingText(
                request.getTitle(),
                request.getDescription(),
                request.getType(),
                request.getPriority()
        );

        // Generate embedding vector
        PGvector queryEmbedding = embeddingService.generateEmbedding(embeddingText);
        String queryEmbeddingStr = PGvectorUtil.toArrayString(queryEmbedding);

        // Search similar tasks
        List<Task> similarTasks = taskRepository.findSimilarTasksByEmbedding(
                request.getProjectId(),
                queryEmbeddingStr,
                request.getTitle() != null ? request.getTitle() : "",
                request.getLimit()
        );

        // Convert to response DTOs with similarity scores
        List<SimilarTaskResponse> responses = new ArrayList<>();
        for (Task task : similarTasks) {
            if (task.getEmbedding() != null) {
                double similarity = calculateCosineSimilarity(queryEmbedding, task.getEmbedding());

                SimilarTaskResponse response = new SimilarTaskResponse();
                response.setId(task.getId());
                response.setTaskKey(task.getTaskKey());
                response.setTitle(task.getTitle());
                response.setDescription(task.getDescription());
                response.setType(task.getType().name());
                response.setStatus(task.getStatus().name());
                response.setPriority(task.getPriority().name());
                response.setStoryPoints(task.getStoryPoints());
                response.setSimilarityScore(similarity);

                responses.add(response);
            }
        }

        return responses;
    }

    /**
     * Generate embedding for a task and save it
     */
    @Transactional
    public Task generateAndSaveEmbedding(Long taskId) {
        if (embeddingService == null) {
            log.error("Embedding service not available");
            throw new IllegalStateException("Embedding service not available");
        }

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        String embeddingText = buildEmbeddingText(
                task.getTitle(),
                task.getDescription(),
                task.getType().name(),
                task.getPriority().name()
        );

        PGvector embedding = embeddingService.generateEmbedding(embeddingText);
        task.setEmbedding(embedding);

        return taskRepository.save(task);
    }

    /**
     * Batch generate embeddings for tasks without embeddings
     */
    @Transactional
    public int batchGenerateEmbeddings(Long projectId, int batchSize) {
        if (embeddingService == null) {
            log.error("Embedding service not available");
            throw new IllegalStateException("Embedding service not available");
        }

        List<Task> tasks = projectId != null
                ? taskRepository.findByProjectId(projectId)
                : taskRepository.findAll();

        int count = 0;
        for (Task task : tasks) {
            if (task.getEmbedding() == null && count < batchSize) {
                try {
                    String embeddingText = buildEmbeddingText(
                            task.getTitle(),
                            task.getDescription(),
                            task.getType().name(),
                            task.getPriority().name()
                    );

                    PGvector embedding = embeddingService.generateEmbedding(embeddingText);
                    task.setEmbedding(embedding);
                    taskRepository.save(task);
                    count++;

                    log.info("Generated embedding for task: {}", task.getTaskKey());
                } catch (Exception e) {
                    log.error("Failed to generate embedding for task {}: {}", task.getTaskKey(), e.getMessage());
                }
            }
        }

        return count;
    }

    /**
     * Build embedding text from task fields
     */
    private String buildEmbeddingText(String title, String description, String type, String priority) {
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
     * Calculate cosine similarity between two vectors
     */
    private double calculateCosineSimilarity(PGvector v1, PGvector v2) {
        float[] vec1 = PGvectorUtil.toFloatArray(v1);
        float[] vec2 = PGvectorUtil.toFloatArray(v2);

        if (vec1.length != vec2.length) {
            throw new IllegalArgumentException("Vectors must have same dimension");
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
