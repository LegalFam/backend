package com.legalfam.backend.payment.domain.model;

import com.legalfam.backend.payment.domain.exception.InsufficientTokensException;
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

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public SubscriptionPlanCode getPlanCode() {
        return planCode;
    }

    public void setPlanCode(SubscriptionPlanCode planCode) {
        this.planCode = planCode;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public void setStatus(SubscriptionStatus status) {
        this.status = status;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public void setProvider(PaymentProvider provider) {
        this.provider = provider;
    }

    public String getGatewayCustomerId() {
        return gatewayCustomerId;
    }

    public void setGatewayCustomerId(String gatewayCustomerId) {
        this.gatewayCustomerId = gatewayCustomerId;
    }

    public String getGatewaySubscriptionId() {
        return gatewaySubscriptionId;
    }

    public void setGatewaySubscriptionId(String gatewaySubscriptionId) {
        this.gatewaySubscriptionId = gatewaySubscriptionId;
    }

    public Instant getCurrentPeriodStart() {
        return currentPeriodStart;
    }

    public void setCurrentPeriodStart(Instant currentPeriodStart) {
        this.currentPeriodStart = currentPeriodStart;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public void setCurrentPeriodEnd(Instant currentPeriodEnd) {
        this.currentPeriodEnd = currentPeriodEnd;
    }

    public boolean isCancelAtPeriodEnd() {
        return cancelAtPeriodEnd;
    }

    public void setCancelAtPeriodEnd(boolean cancelAtPeriodEnd) {
        this.cancelAtPeriodEnd = cancelAtPeriodEnd;
    }

    public int getMonthlyTokenLimit() {
        return monthlyTokenLimit;
    }

    public void setMonthlyTokenLimit(int monthlyTokenLimit) {
        this.monthlyTokenLimit = monthlyTokenLimit;
    }

    public int getRemainingTokens() {
        return remainingTokens;
    }

    public void setRemainingTokens(int remainingTokens) {
        this.remainingTokens = remainingTokens;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void consumeChatToken(Instant now) {
        assertActive();
        if (remainingTokens <= 0) {
            throw new InsufficientTokensException("No chat tokens remaining for the current period");
        }
        remainingTokens--;
        updatedAt = now;
    }

    public int refundChatToken(Instant now) {
        int nextRemainingTokens = Math.min(monthlyTokenLimit, remainingTokens + 1);
        int delta = nextRemainingTokens - remainingTokens;
        remainingTokens = nextRemainingTokens;
        updatedAt = now;
        return delta;
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
        if (createdAt == null) {
            createdAt = now;
        }
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

    public boolean hasActiveGatewaySubscription() {
        return provider == PaymentProvider.MERCADO_PAGO
                && gatewaySubscriptionId != null
                && !gatewaySubscriptionId.isBlank()
                && status != SubscriptionStatus.CANCELED
                && status != SubscriptionStatus.EXPIRED;
    }

    private void assertActive() {
        if (status != SubscriptionStatus.ACTIVE) {
            throw new SubscriptionInactiveException("Subscription is not active");
        }
    }
}
