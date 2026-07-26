package com.legalfam.backend.auth.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.frontend")
public record FrontendProperties(
        @NotBlank
        String baseUrl,

        @NotBlank
        String verifyEmailPath,

        @NotBlank
        String resetPasswordPath
) {
}
