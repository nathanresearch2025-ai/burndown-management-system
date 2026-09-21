package com.burndown.controller;

import com.burndown.dto.SimilarTaskResponse;
import com.burndown.dto.SimilarTaskSearchRequest;
import com.burndown.entity.Task;
import com.burndown.service.VectorSimilarityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for vector similarity search
 */
@Slf4j
@RestController
@RequestMapping("/similarity")
public class VectorSimilarityController {

    private final VectorSimilarityService vectorSimilarityService;

    public VectorSimilarityController(VectorSimilarityService vectorSimilarityService) {
        this.vectorSimilarityService = vectorSimilarityService;
    }

    /**
     * Find similar tasks based on vector similarity
     */
    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> searchSimilarTasks(@RequestBody SimilarTaskSearchRequest request) {
        try {
            long startTime = System.currentTimeMillis();

            List<SimilarTaskResponse> similarTasks = vectorSimilarityService.findSimilarTasks(request);

            long duration = System.currentTimeMillis() - startTime;

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "count", similarTasks.size(),
                    "tasks", similarTasks,
                    "durationMs", duration
            ));
        } catch (Exception e) {
            log.error("Failed to search similar tasks", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Generate embedding for a specific task
     */
    @PostMapping("/tasks/{taskId}/embedding")
    public ResponseEntity<Map<String, Object>> generateTaskEmbedding(@PathVariable Long taskId) {
        try {
            long startTime = System.currentTimeMillis();

            Task task = vectorSimilarityService.generateAndSaveEmbedding(taskId);

            long duration = System.currentTimeMillis() - startTime;

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "taskId", task.getId(),
                    "taskKey", task.getTaskKey(),
                    "hasEmbedding", task.getEmbedding() != null,
                    "durationMs", duration
            ));
        } catch (Exception e) {
            log.error("Failed to generate embedding for task {}", taskId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Batch generate embeddings for tasks
     */
    @PostMapping("/batch-generate")
    public ResponseEntity<Map<String, Object>> batchGenerateEmbeddings(
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "10") int batchSize) {
        try {
            long startTime = System.currentTimeMillis();

            int count = vectorSimilarityService.batchGenerateEmbeddings(projectId, batchSize);

            long duration = System.currentTimeMillis() - startTime;

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "processedCount", count,
                    "durationMs", duration
            ));
        } catch (Exception e) {
            log.error("Failed to batch generate embeddings", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
}
