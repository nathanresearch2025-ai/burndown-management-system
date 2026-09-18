package com.burndown.controller;

import com.burndown.service.UnifiedEmbeddingService;
import com.burndown.util.PGvectorUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for embedding service management and testing
 */
@Slf4j
@RestController
@RequestMapping("/embeddings")
@Tag(name = "Embedding Management", description = "Embedding service configuration and testing")
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true")
public class EmbeddingController {

    private final UnifiedEmbeddingService embeddingService;

    public EmbeddingController(UnifiedEmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @GetMapping("/info")
    @Operation(summary = "Get embedding provider information")
    public ResponseEntity<Map<String, Object>> getProviderInfo() {
        return ResponseEntity.ok(embeddingService.getProviderInfo());
    }

    @PostMapping("/test")
    @Operation(summary = "Test embedding generation")
    public ResponseEntity<Map<String, Object>> testEmbedding(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Text is required"));
        }

        long startTime = System.currentTimeMillis();
        var embedding = embeddingService.generateEmbedding(text);
        long duration = System.currentTimeMillis() - startTime;

        int dimension = PGvectorUtil.getDimension(embedding);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "dimension", dimension,
            "durationMs", duration,
            "provider", embeddingService.getProviderInfo()
        ));
    }
}
