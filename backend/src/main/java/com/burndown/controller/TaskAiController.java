package com.burndown.controller;

import com.burndown.config.JwtTokenProvider;
import com.burndown.dto.AiGenerationFeedbackRequest;
import com.burndown.dto.GenerateTaskDescriptionRequest;
import com.burndown.dto.GenerateTaskDescriptionResponse;
import com.burndown.exception.BusinessException;
import com.burndown.service.RateLimitService;
import com.burndown.service.TaskAiService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/tasks/ai")
public class TaskAiController {

    private final TaskAiService taskAiService;
    private final RateLimitService rateLimitService;

    @Value("${ai.rate-limit.max-requests-per-user:10}")
    private int maxRequestsPerUser;

    @Value("${ai.rate-limit.window-minutes:60}")
    private int windowMinutes;

    public TaskAiController(TaskAiService taskAiService, RateLimitService rateLimitService) {
        this.taskAiService = taskAiService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/generate-description")
    public ResponseEntity<GenerateTaskDescriptionResponse> generateDescription(
            @Valid @RequestBody GenerateTaskDescriptionRequest request,
            Authentication authentication) {
        JwtTokenProvider.UserPrincipal principal = (JwtTokenProvider.UserPrincipal) authentication.getPrincipal();

        // Rate limit check.
        String rateLimitKey = "ai_gen_user_" + principal.getId();
        boolean allowed = rateLimitService.checkRateLimit(
                rateLimitKey,
                maxRequestsPerUser,
                Duration.ofMinutes(windowMinutes)
        );

        if (!allowed) {
            long resetTime = rateLimitService.getResetTime(rateLimitKey);
            throw new BusinessException(
                    "RATE_LIMIT_EXCEEDED",
                    "task.ai.rateLimitExceeded",
                    HttpStatus.TOO_MANY_REQUESTS,
                    resetTime
            );
        }

        GenerateTaskDescriptionResponse response = taskAiService.generateDescription(request, principal.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/feedback")
    public ResponseEntity<Void> submitFeedback(
            @Valid @RequestBody AiGenerationFeedbackRequest request,
            Authentication authentication) {
        JwtTokenProvider.UserPrincipal principal = (JwtTokenProvider.UserPrincipal) authentication.getPrincipal();
        taskAiService.submitFeedback(request, principal.getId());
        return ResponseEntity.ok().build();
    }
}
