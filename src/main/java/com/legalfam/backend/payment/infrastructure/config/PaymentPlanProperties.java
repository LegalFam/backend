package com.legalfam.backend.payment.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.payment.plans")
public record PaymentPlanProperties(
        @Valid @NotNull Plan free,
        @Valid @NotNull Plan basic,
        @Valid @NotNull Plan premium
) {
    public record Plan(
            @NotBlank String displayName,
            String description,
            @NotNull @PositiveOrZero Integer tokens,
            @NotNull @PositiveOrZero Integer monthlyPriceCents,
            String currency
    ) {
    }
}
