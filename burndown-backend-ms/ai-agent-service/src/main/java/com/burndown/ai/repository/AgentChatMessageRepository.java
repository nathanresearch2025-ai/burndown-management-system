package com.burndown.ai.repository;

import com.burndown.ai.entity.AgentChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentChatMessageRepository extends JpaRepository<AgentChatMessage, Long> {
    List<AgentChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
}
