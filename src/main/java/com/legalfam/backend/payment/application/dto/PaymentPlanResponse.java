package com.legalfam.backend.payment.application.dto;

public record PaymentPlanResponse(
        String code,
        String displayName,
        String description,
        String billingInterval,
        Integer monthlyPriceCents,
        String currency,
        int monthlyTokenLimit,
        boolean currentPlan,
        boolean purchasable
) {
}
