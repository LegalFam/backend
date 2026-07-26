package com.legalfam.backend.auth.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Named AuthMailProperties rather than MailProperties so it does not collide with
 * Spring Boot's own org.springframework.boot.autoconfigure.mail.MailProperties.
 */
@Validated
@ConfigurationProperties(prefix = "app.auth.mail")
public record AuthMailProperties(
        boolean enabled,

        @NotBlank
        String from,

        @NotBlank
        String fromName,

        String replyTo
) {
}
