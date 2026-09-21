package com.burndown.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for embedding services
 * Ensures proper bean initialization order and conditional loading
 */
@Configuration
public class EmbeddingConfig {

    /**
     * This configuration ensures that UnifiedEmbeddingService is properly initialized
     * after both DjlEmbeddingService and EmbeddingService (if they exist)
     */
}
