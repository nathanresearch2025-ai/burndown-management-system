package com.burndown.aiagent.standup.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_chat_message")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "question", columnDefinition = "TEXT")
    private String question;

    @Column(name = "answer", columnDefinition = "TEXT")
    private String answer;

    @Column(name = "intent", length = 50)
    private String intent;

    @Type(JsonBinaryType.class)
    @Column(name = "tools_used", columnDefinition = "jsonb")
    private String toolsUsed;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
