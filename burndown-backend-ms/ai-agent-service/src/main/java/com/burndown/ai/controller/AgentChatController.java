package com.burndown.ai.controller;

import com.burndown.ai.entity.AgentChatMessage;
import com.burndown.ai.entity.AgentChatSession;
import com.burndown.ai.service.AgentChatService;
import com.burndown.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai/sessions")
@RequiredArgsConstructor
public class AgentChatController {

    private final AgentChatService agentChatService;

    @PostMapping
    public ResponseEntity<ApiResponse<AgentChatSession>> createSession(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody Map<String, Object> body) {
        Long projectId = body.get("projectId") != null
                ? Long.valueOf(body.get("projectId").toString()) : null;
        String title = (String) body.get("title");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(agentChatService.createSession(userId, projectId, title)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AgentChatSession>>> getSessions(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                agentChatService.getUserSessions(userId, PageRequest.of(page, size))));
    }

    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<ApiResponse<List<AgentChatMessage>>> getMessages(
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(ApiResponse.ok(agentChatService.getMessages(sessionId)));
    }

    @PostMapping("/{sessionId}/chat")
    public ResponseEntity<ApiResponse<AgentChatMessage>> chat(
            @PathVariable Long sessionId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody Map<String, String> body) {
        String message = body.get("message");
        return ResponseEntity.ok(ApiResponse.ok(
                agentChatService.chat(sessionId, userId, message)));
    }

    @PatchMapping("/{sessionId}/end")
    public ResponseEntity<ApiResponse<Void>> endSession(@PathVariable Long sessionId) {
        agentChatService.endSession(sessionId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Session ended"));
    }
}
