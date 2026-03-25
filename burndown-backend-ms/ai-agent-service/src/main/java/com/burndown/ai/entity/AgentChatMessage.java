package com.burndown.ai.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "agent_chat_messages", schema = "ms_ai")
@EntityListeners(AuditingEntityListener.class)
public class AgentChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(length = 20)
    private String role; // user, assistant, system

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "token_count")
    private Integer tokenCount;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
