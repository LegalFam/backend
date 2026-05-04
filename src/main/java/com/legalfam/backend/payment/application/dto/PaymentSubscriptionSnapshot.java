package com.legalfam.backend.payment.application.dto;

import java.time.Instant;
import java.util.UUID;

public record PaymentSubscriptionSnapshot(
        String customerId,
        String subscriptionId,
        UUID userId,
        String planCode,
        String status,
        boolean cancelAtPeriodEnd,
        Instant currentPeriodStart,
        Instant currentPeriodEnd
) {
}
