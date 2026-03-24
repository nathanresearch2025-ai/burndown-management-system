package com.burndown.gateway.config;

import com.burndown.gateway.filter.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * On startup, fetches the RSA public key from auth-service and injects it into JwtAuthFilter.
 * Retries up to 10 times with 3-second delay to handle auth-service startup order.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublicKeyLoader {

    @Value("${auth-service.url:http://auth-service}")
    private String authServiceUrl;

    private final JwtAuthFilter jwtAuthFilter;

    @EventListener(ApplicationReadyEvent.class)
    public void loadPublicKey() {
        WebClient client = WebClient.create(authServiceUrl);
        for (int i = 0; i < 10; i++) {
            try {
                String base64Key = client.get()
                        .uri("/api/v1/auth/public-key")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                byte[] keyBytes = Base64.getDecoder().decode(base64Key);
                PublicKey publicKey = KeyFactory.getInstance("RSA")
                        .generatePublic(new X509EncodedKeySpec(keyBytes));
                jwtAuthFilter.setPublicKey(publicKey);
                log.info("RSA public key loaded from auth-service successfully");
                return;
            } catch (Exception e) {
                log.warn("Attempt {}/10 to load public key failed: {}", i + 1, e.getMessage());
                try { Thread.sleep(3000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        log.error("Failed to load public key after 10 attempts — JWT validation will fail!");
    }
}
