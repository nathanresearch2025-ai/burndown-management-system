package com.burndown.aiagent.langchain.controller;

import com.burndown.aiagent.langchain.dto.LangchainStandupRequest;
import com.burndown.aiagent.langchain.dto.LangchainStandupResponse;
import com.burndown.aiagent.langchain.service.LangchainClientService;
import com.burndown.aiagent.standup.dto.ApiResponse;
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
@RequestMapping("/agent/langchain")
@RequiredArgsConstructor
@Tag(name = "LangChain Standup Agent", description = "LangChain Standup Agent API")
public class LangchainStandupController {

    private final LangchainClientService langchainClientService;

    @PostMapping("/standup/query")
    @Operation(summary = "LangChain standup Q&A", description = "Handles standup questions via the LangChain service")
    public ApiResponse<LangchainStandupResponse> query(
            @Valid @RequestBody LangchainStandupRequest request,
            Authentication authentication) {

        String traceId = UUID.randomUUID().toString().substring(0, 16); // generate traceId
        request.setTraceId(traceId);

        if (authentication == null || authentication.getPrincipal() == null) {
            request.setUserId(1L); // set default user ID when authentication is absent
        }

        LangchainStandupResponse response = langchainClientService.queryStandup(request);
        return ApiResponse.success(response, traceId);
    }
}
