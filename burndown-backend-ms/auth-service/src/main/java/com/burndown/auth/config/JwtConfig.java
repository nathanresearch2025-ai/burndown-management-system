package com.burndown.auth.config;

import com.burndown.common.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

@Slf4j
@Configuration
public class JwtConfig {

    private final KeyPair keyPair;

    public JwtConfig() {
        // Generate RSA key pair at startup
        // In production: load from keystore or environment variable
        log.info("Generating RSA key pair for JWT signing...");
        this.keyPair = JwtUtil.generateKeyPair();
        log.info("RSA key pair generated successfully");
    }

    @Bean
    public PrivateKey jwtPrivateKey() {
        return keyPair.getPrivate();
    }

    @Bean
    public PublicKey jwtPublicKey() {
        return keyPair.getPublic();
    }
}
