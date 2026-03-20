package com.burndown.service;

import com.burndown.entity.Task;
import com.burndown.repository.TaskRepository;
import com.pgvector.PGvector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Batch-generates vector embeddings for existing tasks that do not yet have one.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true")
public class TaskEmbeddingBatchService {

    private final TaskRepository taskRepository;
    private final UnifiedEmbeddingService embeddingService;

    public TaskEmbeddingBatchService(TaskRepository taskRepository, UnifiedEmbeddingService embeddingService) {
        this.taskRepository = taskRepository;
        this.embeddingService = embeddingService;
    }

    /**
     * Generate embeddings for all tasks that do not yet have a vector.
     * @param batchSize number of tasks to process per batch
     * @return total number of tasks processed
     */
    @Transactional
    public int generateEmbeddingsForTasksWithoutVectors(int batchSize) {
        int totalProcessed = 0;
        int currentBatch = 0;

        while (true) {
            List<Task> tasks = taskRepository.findTasksWithoutEmbeddings(PageRequest.of(0, batchSize));

            if (tasks.isEmpty()) {
                break;
            }

            log.info("Processing batch {} with {} tasks", ++currentBatch, tasks.size());

            for (Task task : tasks) {
                try {
                    generateAndSaveEmbedding(task);
                    totalProcessed++;
                } catch (Exception ex) {
                    log.error("Failed to generate embedding for task {}: {}", task.getId(), ex.getMessage());
                }
            }

            log.info("Batch {} completed. Total processed: {}", currentBatch, totalProcessed);
        }

        log.info("Embedding generation completed. Total tasks processed: {}", totalProcessed);
        return totalProcessed;
    }

    /**
     * Generate and persist the embedding vector for a single task.
     */
    @Transactional
    public void generateAndSaveEmbedding(Task task) {
        String embeddingText = embeddingService.buildTaskEmbeddingText(
                task.getTitle(),
                task.getDescription(),
                task.getType() != null ? task.getType().name() : null,
                task.getPriority() != null ? task.getPriority().name() : null
        );

        PGvector embedding = embeddingService.generateEmbedding(embeddingText);
        task.setEmbedding(embedding);
        taskRepository.save(task);

        log.debug("Generated embedding for task {}: {}", task.getId(), task.getTitle());
    }

    /**
     * Regenerate embeddings for all tasks (use with caution).
     */
    @Transactional
    public int regenerateAllEmbeddings(int batchSize) {
        int totalProcessed = 0;
        int page = 0;

        while (true) {
            List<Task> tasks = taskRepository.findAll(PageRequest.of(page++, batchSize)).getContent();

            if (tasks.isEmpty()) {
                break;
            }

            log.info("Regenerating embeddings for page {} with {} tasks", page, tasks.size());

            for (Task task : tasks) {
                try {
                    generateAndSaveEmbedding(task);
                    totalProcessed++;
                } catch (Exception ex) {
                    log.error("Failed to regenerate embedding for task {}: {}", task.getId(), ex.getMessage());
                }
            }
        }

        log.info("Embedding regeneration completed. Total tasks processed: {}", totalProcessed);
        return totalProcessed;
    }
}
