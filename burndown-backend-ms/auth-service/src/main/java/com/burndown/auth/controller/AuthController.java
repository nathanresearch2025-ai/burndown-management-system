package com.burndown.auth.controller;

import com.burndown.auth.dto.LoginRequest;
import com.burndown.auth.dto.LoginResponse;
import com.burndown.auth.dto.RegisterRequest;
import com.burndown.auth.entity.User;
import com.burndown.auth.service.AuthService;
import com.burndown.common.dto.ApiResponse;
import com.burndown.common.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.PublicKey;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PublicKey jwtPublicKey;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Login successful"));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Long>> register(
            @Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(user.getId(), "Registration successful"));
    }

    /**
     * Exposes the RSA public key (Base64) for other services to verify JWT tokens.
     * Called by API Gateway on startup.
     */
    @GetMapping("/public-key")
    public ResponseEntity<String> getPublicKey() {
        return ResponseEntity.ok(JwtUtil.encodePublicKey(jwtPublicKey));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Long>> getCurrentUser(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(userId));
    }
}
