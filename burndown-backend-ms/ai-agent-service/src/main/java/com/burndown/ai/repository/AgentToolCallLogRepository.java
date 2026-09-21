package com.burndown.ai.repository;

import com.burndown.ai.entity.AgentToolCallLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentToolCallLogRepository extends JpaRepository<AgentToolCallLog, Long> {
    List<AgentToolCallLog> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
}
