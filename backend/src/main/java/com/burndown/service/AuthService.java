package com.burndown.service;

import com.burndown.config.JwtTokenProvider;
import com.burndown.dto.AuthResponse;
import com.burndown.dto.LoginRequest;
import com.burndown.dto.RegisterRequest;
import com.burndown.dto.UserResponse;
import com.burndown.entity.User;
import com.burndown.exception.BusinessException;
import com.burndown.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRoleService userRoleService;
    private final RoleService roleService;

    public AuthService(UserRepository userRepository,
                      PasswordEncoder passwordEncoder,
                      JwtTokenProvider jwtTokenProvider,
                      UserRoleService userRoleService,
                      RoleService roleService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRoleService = userRoleService;
        this.roleService = roleService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("USERNAME_EXISTS", "auth.usernameExists", HttpStatus.CONFLICT);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("EMAIL_EXISTS", "auth.emailExists", HttpStatus.CONFLICT);
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setIsActive(true);
        user.setIsEmailVerified(false);

        user = userRepository.save(user);

        // Assign role.
        if (request.getRoleId() != null) {
            // Verify the role is available for self-registration.
            roleService.getAvailableRolesForRegistration().stream()
                    .filter(role -> role.getId().equals(request.getRoleId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("INVALID_ROLE", "auth.invalidRole", HttpStatus.BAD_REQUEST));

            userRoleService.assignRoleToUser(user.getId(), request.getRoleId());
        }

        // Load user permissions — uses an optimized query method.
        Set<String> permissions = userRoleService.getUserPermissionCodes(user.getId());

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), permissions);
        UserResponse userResponse = mapToUserResponse(user);

        return new AuthResponse(token, userResponse);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        System.out.println("DEBUG: Login attempt for username: " + request.getUsername());
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        System.out.println("DEBUG: User found: " + user.getUsername());
        System.out.println("DEBUG: Password hash from DB: " + user.getPasswordHash());
        System.out.println("DEBUG: Password from request: " + request.getPassword());
        boolean matches = passwordEncoder.matches(request.getPassword(), user.getPasswordHash());
        System.out.println("DEBUG: Password matches: " + matches);

        if (!matches) {
            throw new BadCredentialsException("Invalid credentials");
        }

        if (!user.getIsActive()) {
            throw new BusinessException("ACCOUNT_DISABLED", "auth.accountDisabled", HttpStatus.FORBIDDEN);
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // Load user permissions — uses an optimized query method.
        Set<String> permissions = userRoleService.getUserPermissionCodes(user.getId());

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), permissions);
        UserResponse userResponse = mapToUserResponse(user);

        return new AuthResponse(token, userResponse);
    }

    private UserResponse mapToUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setFullName(user.getFullName());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setCreatedAt(user.getCreatedAt());
        return response;
    }
}
