package com.legalfam.backend.chat.infrastructure.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.chat.sse")
public record ChatSseProperties(
        @Positive Long emitterTimeoutMs,
        @Positive Long heartbeatIntervalMs
) {
    public long safeEmitterTimeoutMs() {
        return emitterTimeoutMs == null ? 1800000L : emitterTimeoutMs;
    }

    public long safeHeartbeatIntervalMs() {
        return heartbeatIntervalMs == null ? 15000L : heartbeatIntervalMs;
    }
}
