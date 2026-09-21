package com.burndown.aiagent.standup.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
@Getter
public class StandupAgentMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter requestsTotal;
    private final Counter fallbackTotal;
    private final Timer durationTimer;

    public StandupAgentMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.requestsTotal = Counter.builder("standup_agent_requests_total")
                .description("Total number of standup agent requests")
                .register(meterRegistry);

        this.fallbackTotal = Counter.builder("standup_agent_fallback_total")
                .description("Total number of fallback responses")
                .register(meterRegistry);

        this.durationTimer = Timer.builder("standup_agent_duration_ms")
                .description("Duration of standup agent requests in milliseconds")
                .register(meterRegistry);
    }

    public void incrementToolCalls(String toolName) {
        Counter.builder("standup_agent_tool_calls_total")
                .tag("tool_name", toolName)
                .description("Total number of tool calls")
                .register(meterRegistry)
                .increment();
    }

    public void incrementToolFailures(String toolName) {
        Counter.builder("standup_agent_tool_failures_total")
                .tag("tool_name", toolName)
                .description("Total number of tool call failures")
                .register(meterRegistry)
                .increment();
    }
}
