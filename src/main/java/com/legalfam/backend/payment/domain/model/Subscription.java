package com.legalfam.backend.payment.domain.model;

import com.legalfam.backend.payment.domain.exception.InvalidPaymentRequestException;
import com.legalfam.backend.payment.domain.exception.SubscriptionInactiveException;
import java.time.Instant;
import java.util.UUID;

public class Subscription {

    private UUID id;
    private UUID userId;
    private SubscriptionPlanCode planCode;
    private SubscriptionStatus status;
    private PaymentProvider provider;
    private String gatewayCustomerId;
    private String gatewaySubscriptionId;
    private Instant currentPeriodStart;
    private Instant currentPeriodEnd;
    private boolean cancelAtPeriodEnd;
    private int monthlyTokenLimit;
    private int remainingTokens;
    private Instant createdAt;
    private Instant updatedAt;

    private Subscription() {
    }

    public static Subscription createFree(UUID userId, SubscriptionPlanCode planCode, int tokenLimit, Instant periodStart, Instant periodEnd, Instant now) {
        Subscription subscription = new Subscription();
        subscription.userId = userId;
        subscription.activateFreePlan(planCode, tokenLimit, periodStart, periodEnd, now);
        return subscription;
    }

    public static Subscription restore(
            UUID id,
            UUID userId,
            SubscriptionPlanCode planCode,
            SubscriptionStatus status,
            PaymentProvider provider,
            String gatewayCustomerId,
            String gatewaySubscriptionId,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            boolean cancelAtPeriodEnd,
            int monthlyTokenLimit,
            int remainingTokens,
            Instant createdAt,
            Instant updatedAt
    ) {
        Subscription subscription = new Subscription();
        subscription.id = id;
        subscription.userId = userId;
        subscription.planCode = planCode;
        subscription.status = status;
        subscription.provider = provider;
        subscription.gatewayCustomerId = gatewayCustomerId;
        subscription.gatewaySubscriptionId = gatewaySubscriptionId;
        subscription.currentPeriodStart = currentPeriodStart;
        subscription.currentPeriodEnd = currentPeriodEnd;
        subscription.cancelAtPeriodEnd = cancelAtPeriodEnd;
        subscription.monthlyTokenLimit = monthlyTokenLimit;
        subscription.remainingTokens = remainingTokens;
        subscription.createdAt = createdAt;
        subscription.updatedAt = updatedAt;
        return subscription;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public SubscriptionPlanCode getPlanCode() {
        return planCode;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public String getGatewayCustomerId() {
        return gatewayCustomerId;
    }

    public String getGatewaySubscriptionId() {
        return gatewaySubscriptionId;
    }

    public Instant getCurrentPeriodStart() {
        return currentPeriodStart;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public boolean isCancelAtPeriodEnd() {
        return cancelAtPeriodEnd;
    }

    public int getMonthlyTokenLimit() {
        return monthlyTokenLimit;
    }

    public int getRemainingTokens() {
        return remainingTokens;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int consumeAvailableChatTokens(int requestedTokens, Instant now) {
        assertActive();
        if (requestedTokens <= 0 || remainingTokens <= 0) {
            return 0;
        }
        int consumedTokens = Math.min(requestedTokens, remainingTokens);
        remainingTokens -= consumedTokens;
        updatedAt = now;
        return consumedTokens;
    }

    public boolean shouldRefreshFreePeriodAt(Instant now) {
        return provider == PaymentProvider.FREE && (currentPeriodEnd == null || !now.isBefore(currentPeriodEnd));
    }

    public void assertCheckoutAllowedFor(SubscriptionPlanCode targetPlanCode) {
        if (planCode == targetPlanCode
                && provider == PaymentProvider.MERCADO_PAGO
                && status == SubscriptionStatus.ACTIVE) {
            throw new InvalidPaymentRequestException("User is already subscribed to the selected plan");
        }
        if (hasActiveGatewaySubscription()) {
            throw new InvalidPaymentRequestException("Cancel the current Mercado Pago subscription before changing plans");
        }
    }

    public void activateFreePlan(
            SubscriptionPlanCode planCode,
            int tokenLimit,
            Instant periodStart,
            Instant periodEnd,
            Instant now
    ) {
        this.planCode = planCode;
        this.status = SubscriptionStatus.ACTIVE;
        this.provider = PaymentProvider.FREE;
        this.gatewayCustomerId = null;
        this.gatewaySubscriptionId = null;
        this.currentPeriodStart = periodStart;
        this.currentPeriodEnd = periodEnd;
        this.cancelAtPeriodEnd = false;
        this.monthlyTokenLimit = tokenLimit;
        this.remainingTokens = tokenLimit;
        if (createdAt == null) { createdAt = now; }
        updatedAt = now;
    }

    public int allocateCurrentPeriodTokens(Instant periodStart, Instant periodEnd, Instant now) {
        int delta = monthlyTokenLimit - remainingTokens;
        currentPeriodStart = periodStart;
        currentPeriodEnd = periodEnd;
        remainingTokens = monthlyTokenLimit;
        status = SubscriptionStatus.ACTIVE;
        updatedAt = now;
        return delta;
    }

    public void syncGatewaySubscription(
            SubscriptionPlanCode planCode,
            SubscriptionStatus status,
            String gatewayCustomerId,
            String gatewaySubscriptionId,
            Instant periodStart,
            Instant periodEnd,
            boolean cancelAtPeriodEnd,
            int monthlyTokenLimit,
            boolean resetPeriodTokens,
            Instant now
    ) {
        SubscriptionPlanCode previousPlan = this.planCode;
        int previousLimit = this.monthlyTokenLimit;
        int previousRemainingTokens = this.remainingTokens;

        this.planCode = planCode;
        this.status = status;
        this.provider = PaymentProvider.MERCADO_PAGO;
        this.gatewayCustomerId = gatewayCustomerId;
        this.gatewaySubscriptionId = gatewaySubscriptionId;
        this.currentPeriodStart = periodStart;
        this.currentPeriodEnd = periodEnd;
        this.cancelAtPeriodEnd = cancelAtPeriodEnd;
        this.monthlyTokenLimit = monthlyTokenLimit;

        if (createdAt == null) { createdAt = now; }
        if (resetPeriodTokens) {
            remainingTokens = monthlyTokenLimit;
        } else if (previousPlan != planCode) {
            int usedTokens = Math.max(previousLimit - previousRemainingTokens, 0);
            remainingTokens = Math.max(monthlyTokenLimit - usedTokens, 0);
        }
        updatedAt = now;
    }

    public boolean hasActiveGatewaySubscription() {
        return hasGatewaySubscription()
                && status != SubscriptionStatus.CANCELED
                && status != SubscriptionStatus.EXPIRED;
    }

    public boolean hasGatewaySubscription() {
        return provider == PaymentProvider.MERCADO_PAGO && gatewaySubscriptionId != null && !gatewaySubscriptionId.isBlank();
    }

    private void assertActive() {
        if (status != SubscriptionStatus.ACTIVE) { throw new SubscriptionInactiveException("Subscription is not active"); }
    }
}
