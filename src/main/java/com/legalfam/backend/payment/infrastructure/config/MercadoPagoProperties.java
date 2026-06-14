package com.legalfam.backend.payment.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.payment.mercado-pago")
public record MercadoPagoProperties(
        String accessToken,
        String apiBaseUrl,
        String webhookSecret,

        @NotBlank
        String checkoutSuccessUrl,

        @NotBlank
        String checkoutCancelUrl
) {
    public String normalizedApiBaseUrl() {
        return apiBaseUrl == null || apiBaseUrl.isBlank()
                ? "https://api.mercadopago.com"
                : apiBaseUrl.trim().replaceAll("/+$", "");
    }

    public String normalizedAccessToken() {
        return accessToken == null ? "" : accessToken.trim();
    }

    public String normalizedWebhookSecret() {
        return webhookSecret == null ? "" : webhookSecret.trim();
    }
}
