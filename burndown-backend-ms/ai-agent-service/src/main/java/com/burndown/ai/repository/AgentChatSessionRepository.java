package com.burndown.ai.repository;

import com.burndown.ai.entity.AgentChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgentChatSessionRepository extends JpaRepository<AgentChatSession, Long> {
    Page<AgentChatSession> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Optional<AgentChatSession> findBySessionKey(String sessionKey);
}
