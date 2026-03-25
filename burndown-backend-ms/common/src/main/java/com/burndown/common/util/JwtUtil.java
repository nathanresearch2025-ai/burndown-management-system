package com.burndown.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.List;
@Component
public class JwtUtil {

    private static final long EXPIRATION_MS = 7L * 24 * 60 * 60 * 1000; // 7 days

    /** Generate a new RSA key pair (call once at auth-service startup) */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate RSA key pair", e);
        }
    }

    /** Encode public key to Base64 string for transfer over HTTP */
    public static String encodePublicKey(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /** Decode Base64 public key string back to PublicKey */
    public static PublicKey decodePublicKey(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(bytes);
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode public key", e);
        }
    }

    /** Decode Base64 private key string back to PrivateKey */
    public static PrivateKey decodePrivateKey(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode private key", e);
        }
    }

    /** Sign a JWT with RSA private key */
    public static String generateToken(PrivateKey privateKey, Long userId,
                                       String username, List<String> permissions) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("permissions", permissions)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    /** Verify and parse JWT with RSA public key */
    public static Claims parseToken(PublicKey publicKey, String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @SuppressWarnings("unchecked")
    public static List<String> getPermissions(Claims claims) {
        Object perms = claims.get("permissions");
        if (perms instanceof List) {
            return (List<String>) perms;
        }
        return List.of();
    }
}
