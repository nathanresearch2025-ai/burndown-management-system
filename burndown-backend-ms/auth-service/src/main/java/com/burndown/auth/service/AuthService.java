package com.burndown.auth.service;

import com.burndown.auth.dto.LoginRequest;
import com.burndown.auth.dto.LoginResponse;
import com.burndown.auth.dto.RegisterRequest;
import com.burndown.auth.entity.User;
import com.burndown.auth.repository.UserRepository;
import com.burndown.auth.repository.UserRoleRepository;
import com.burndown.common.exception.BusinessException;
import com.burndown.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.PrivateKey;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PrivateKey jwtPrivateKey;

    private static final long EXPIRATION_DAYS = 7L;

    @Transactional
    public User register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new BusinessException("USERNAME_TAKEN", "Username already taken", HttpStatus.CONFLICT);
        }
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new BusinessException("EMAIL_TAKEN", "Email already registered", HttpStatus.CONFLICT);
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setFullName(req.getFullName());
        user.setIsActive(true);
        return userRepository.save(user);
    }

    @Transactional
    public LoginResponse login(LoginRequest req) {
        User user = userRepository.findByUsernameOrEmail(req.getIdentifier())
                .orElseThrow(() -> new BusinessException("INVALID_CREDENTIALS",
                        "Invalid username or password", HttpStatus.UNAUTHORIZED));

        if (!user.getIsActive()) {
            throw new BusinessException("ACCOUNT_DISABLED", "Account is disabled", HttpStatus.FORBIDDEN);
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("INVALID_CREDENTIALS",
                    "Invalid username or password", HttpStatus.UNAUTHORIZED);
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        Set<String> permSet = getUserPermissions(user.getId());
        List<String> permissions = new ArrayList<>(permSet);

        String token = JwtUtil.generateToken(jwtPrivateKey, user.getId(),
                user.getUsername(), permissions);

        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setExpiresIn(EXPIRATION_DAYS * 24 * 3600);
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setEmail(user.getEmail());
        resp.setFullName(user.getFullName());
        resp.setPermissions(permissions);
        return resp;
    }

    @Cacheable(value = "permissions", key = "#userId")
    public Set<String> getUserPermissions(Long userId) {
        return userRoleRepository.findPermissionCodesByUserId(userId);
    }
}
