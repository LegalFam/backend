package com.legalfam.backend.payment.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCheckoutSessionRequest(
        @NotBlank(message = "Plan code is required")
        @Size(max = 40, message = "Plan code must be at most 40 characters")
        String planCode,

        @Size(max = 2048, message = "Success URL must be at most 2048 characters")
        @Pattern(regexp = "^\\s*$|https?://.+", message = "Success URL must be an HTTP URL")
        String successUrl,

        @Size(max = 2048, message = "Cancel URL must be at most 2048 characters")
        @Pattern(regexp = "^\\s*$|https?://.+", message = "Cancel URL must be an HTTP URL")
        String cancelUrl
) {
}
