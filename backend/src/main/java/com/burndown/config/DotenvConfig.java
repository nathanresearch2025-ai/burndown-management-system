package com.burndown.config;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Loads environment variables from the .env file.
 */
@Slf4j
@Configuration
public class DotenvConfig {

    @PostConstruct
    public void loadEnv() {
        try {
            Dotenv dotenv = Dotenv.configure()
                    .directory("./")
                    .ignoreIfMissing()
                    .load();

            // Set variables from .env into system properties.
            dotenv.entries().forEach(entry -> {
                String key = entry.getKey();
                String value = entry.getValue();

                // Only set the property if the system environment variable is not already defined.
                if (System.getenv(key) == null) {
                    System.setProperty(key, value);
                    log.debug("Loaded environment variable from .env: {}", key);
                }
            });

            log.info("Environment variables loaded from .env file");
        } catch (Exception e) {
            log.warn("Failed to load .env file: {}. Using system environment variables.", e.getMessage());
        }
    }
}
