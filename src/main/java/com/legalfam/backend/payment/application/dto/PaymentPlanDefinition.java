package com.legalfam.backend.payment.application.dto;

import com.legalfam.backend.payment.domain.model.SubscriptionPlanCode;

public record PaymentPlanDefinition(
        SubscriptionPlanCode code,
        String displayName,
        String description,
        int monthlyTokenLimit,
        int monthlyPriceCents,
        String currency,
        int contextMessageLimit,
        Integer historyWindowDays
) {
    public boolean isFree() {
        return code == SubscriptionPlanCode.FREE;
    }

    public boolean isPurchasable() {
        return !isFree() && monthlyPriceCents > 0;
    }
}
