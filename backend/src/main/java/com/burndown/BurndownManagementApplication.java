package com.burndown;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class BurndownManagementApplication {
    public static void main(String[] args) {
        SpringApplication.run(BurndownManagementApplication.class, args);
    }
}
