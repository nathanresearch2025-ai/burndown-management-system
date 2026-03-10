package com.burndown.aiagent.standup.repository;

import com.burndown.aiagent.standup.entity.AgentChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgentChatSessionRepository extends JpaRepository<AgentChatSession, Long> {
    Optional<AgentChatSession> findBySessionKey(String sessionKey);
}
