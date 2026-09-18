package com.burndown.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {
    private boolean enabled = false;
    private String baseUrl = "";
    private String apiKey = "";
    private String chatModel = "";
    private int timeoutSeconds = 30;
    private int maxSimilarTasks = 5;
    private int maxTokens = 2048;
}
