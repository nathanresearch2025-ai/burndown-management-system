package com.burndown.aiagent.standup.controller;

import com.burndown.aiagent.standup.dto.ApiResponse;
import com.burndown.aiagent.standup.dto.StandupQueryRequest;
import com.burndown.aiagent.standup.dto.StandupQueryResponse;
import com.burndown.aiagent.standup.service.StandupAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/agent/standup")
@RequiredArgsConstructor
@Tag(name = "Standup Agent", description = "Scrum standup assistant API")
public class StandupAgentController {

    private final StandupAgentService standupAgentService;

    @PostMapping("/query")
    @Operation(summary = "Standup Q&A", description = "Process standup-related questions and return AI-powered answers")
    public ApiResponse<StandupQueryResponse> query(
            @Valid @RequestBody StandupQueryRequest request,
            Authentication authentication) {

        String traceId = UUID.randomUUID().toString().substring(0, 16);
        log.info("Received standup query - traceId: {}, question: {}", traceId, request.getQuestion());

        try {
            // Get user ID from authentication
            Long userId = getUserIdFromAuth(authentication);

            StandupQueryResponse response = standupAgentService.query(request, userId, traceId);

            log.info("Standup query completed - traceId: {}", traceId);
            return ApiResponse.success(response, traceId);

        } catch (Exception e) {
            log.error("Error processing standup query - traceId: {}, error: {}", traceId, e.getMessage(), e);
            return ApiResponse.error("INTERNAL_ERROR", e.getMessage(), traceId);
        }
    }

    private Long getUserIdFromAuth(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return 1L; // Default user for testing
        }
        // Extract user ID from JWT token
        return 1L; // TODO: implement proper user extraction
    }
}
