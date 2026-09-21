package com.burndown.ai.service;

import com.burndown.ai.entity.AgentChatMessage;
import com.burndown.ai.entity.AgentChatSession;
import com.burndown.ai.repository.AgentChatMessageRepository;
import com.burndown.ai.repository.AgentChatSessionRepository;
import com.burndown.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentChatService {

    private final AgentChatSessionRepository sessionRepository;
    private final AgentChatMessageRepository messageRepository;
    private final AiClientService aiClientService;

    @Transactional
    public AgentChatSession createSession(Long userId, Long projectId, String title) {
        AgentChatSession session = new AgentChatSession();
        session.setUserId(userId);
        session.setProjectId(projectId);
        session.setSessionKey(UUID.randomUUID().toString());
        session.setStatus("ACTIVE");
        session.setTitle(title != null ? title : "新对话");
        return sessionRepository.save(session);
    }

    public Page<AgentChatSession> getUserSessions(Long userId, Pageable pageable) {
        return sessionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public List<AgentChatMessage> getMessages(Long sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Transactional
    public AgentChatMessage chat(Long sessionId, Long userId, String userMessage) {
        sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("AgentChatSession", sessionId));

        // Save user message
        AgentChatMessage userMsg = new AgentChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        messageRepository.save(userMsg);

        // Build conversation history
        List<AgentChatMessage> history = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system",
                "content", "你是一个专业的Scrum敏捷项目管理助手，帮助团队管理Sprint、任务和燃尽图分析。"));
        for (AgentChatMessage msg : history) {
            messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
        }

        // Call AI
        String aiResponse = aiClientService.chat(messages);

        // Save assistant message
        AgentChatMessage assistantMsg = new AgentChatMessage();
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(aiResponse);
        return messageRepository.save(assistantMsg);
    }

    @Transactional
    public void endSession(Long sessionId) {
        AgentChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("AgentChatSession", sessionId));
        session.setStatus("ENDED");
        sessionRepository.save(session);
    }
}
