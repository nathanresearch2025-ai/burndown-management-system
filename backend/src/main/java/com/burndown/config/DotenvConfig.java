package com.burndown.config;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * 加载 .env 文件中的环境变量
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

            // 将 .env 中的变量设置到系统环境变量
            dotenv.entries().forEach(entry -> {
                String key = entry.getKey();
                String value = entry.getValue();

                // 只有当系统环境变量不存在时才设置
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
