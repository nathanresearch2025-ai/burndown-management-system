package com.burndown.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter aiGenerationRequestCounter(MeterRegistry registry) {
        return Counter.builder("ai.generation.requests")
                .description("Total number of AI generation requests")
                .tag("type", "task_description")
                .register(registry);
    }

    @Bean
    public Counter aiGenerationSuccessCounter(MeterRegistry registry) {
        return Counter.builder("ai.generation.success")
                .description("Number of successful AI generations")
                .tag("type", "task_description")
                .register(registry);
    }

    @Bean
    public Counter aiGenerationFailureCounter(MeterRegistry registry) {
        return Counter.builder("ai.generation.failure")
                .description("Number of failed AI generations")
                .tag("type", "task_description")
                .register(registry);
    }

    @Bean
    public Counter aiGenerationFallbackCounter(MeterRegistry registry) {
        return Counter.builder("ai.generation.fallback")
                .description("Number of AI generations using fallback template")
                .tag("type", "task_description")
                .register(registry);
    }

    @Bean
    public Counter aiGenerationCacheHitCounter(MeterRegistry registry) {
        return Counter.builder("ai.generation.cache.hit")
                .description("Number of cache hits for AI generation")
                .tag("type", "task_description")
                .register(registry);
    }

    @Bean
    public Timer aiGenerationTimer(MeterRegistry registry) {
        return Timer.builder("ai.generation.duration")
                .description("Time taken for AI generation")
                .tag("type", "task_description")
                .register(registry);
    }
}
