package com.legalfam.backend.chat.infrastructure.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.chat.outbox.relay")
public record ChatOutboxRelayProperties(
        @Positive Integer batchSize,
        @Positive Long retryDelayMs
) {
    public int safeBatchSize() {
        return batchSize == null ? 50 : batchSize;
    }

    public long safeRetryDelayMs() {
        return retryDelayMs == null ? 600000L : retryDelayMs;
    }
}
