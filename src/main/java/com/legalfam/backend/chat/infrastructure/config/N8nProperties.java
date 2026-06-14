package com.legalfam.backend.chat.infrastructure.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.n8n")
public record N8nProperties(
        String webhookUrl,
        String authHeaderName,
        String authToken,
        @Positive Integer timeoutMs
) {
    public String normalizedWebhookUrl() {
        return webhookUrl == null ? "" : webhookUrl.trim();
    }

    public String normalizedAuthHeaderName() {
        return authHeaderName == null || authHeaderName.isBlank() ? "X-N8N-Token" : authHeaderName.trim();
    }

    public String normalizedAuthToken() {
        return authToken == null ? "" : authToken.trim();
    }

    public int safeTimeoutMs() {
        return timeoutMs == null ? 30000 : timeoutMs;
    }
}
