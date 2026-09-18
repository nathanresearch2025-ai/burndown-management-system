package com.burndown.aiagent.standup.repository;

import com.burndown.aiagent.standup.entity.AgentToolCallLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentToolCallLogRepository extends JpaRepository<AgentToolCallLog, Long> {
    List<AgentToolCallLog> findByMessageIdOrderByCreatedAtAsc(Long messageId);
}
