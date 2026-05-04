package com.legalfam.backend.payment.application.dto;

public record CreateCheckoutSessionRequest(
        String planCode,
        String successUrl,
        String cancelUrl
) {
}
