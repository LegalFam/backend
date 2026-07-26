package com.legalfam.backend.auth.infrastructure.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.auth.tokens")
public record AuthTokenTtlProperties(
        @Positive
        long emailVerificationExpirationMs,

        @Positive
        long passwordResetExpirationMs,

        @Positive
        long mailResendCooldownMs
) {
}
