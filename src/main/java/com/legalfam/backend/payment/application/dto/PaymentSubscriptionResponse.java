package com.legalfam.backend.payment.application.dto;

import java.time.Instant;

public record PaymentSubscriptionResponse(
        String planCode,
        String status,
        String provider,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        int monthlyTokenLimit,
        int remainingTokens
) {
}
