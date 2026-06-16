package com.legalfam.backend.payment.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.legalfam.backend.payment.domain.exception.InsufficientTokensException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubscriptionTest {

    @Test
    void consumeChatTokenDecrementsRemainingTokens() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Subscription subscription = Subscription.createFree(
                UUID.randomUUID(),
                SubscriptionPlanCode.FREE,
                2,
                now,
                Instant.parse("2026-02-01T00:00:00Z"),
                now
        );

        subscription.consumeChatToken(Instant.parse("2026-01-01T00:01:00Z"));

        assertEquals(1, subscription.getRemainingTokens());
        assertEquals(Instant.parse("2026-01-01T00:01:00Z"), subscription.getUpdatedAt());
    }

    @Test
    void consumeAvailableChatTokensConsumesOnlyRemainingBalance() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Subscription subscription = Subscription.createFree(
                UUID.randomUUID(),
                SubscriptionPlanCode.FREE,
                2,
                now,
                Instant.parse("2026-02-01T00:00:00Z"),
                now
        );
        subscription.consumeChatToken(Instant.parse("2026-01-01T00:01:00Z"));

        int consumed = subscription.consumeAvailableChatTokens(2, Instant.parse("2026-01-01T00:02:00Z"));

        assertEquals(1, consumed);
        assertEquals(0, subscription.getRemainingTokens());
        assertEquals(Instant.parse("2026-01-01T00:02:00Z"), subscription.getUpdatedAt());
    }

    @Test
    void consumeChatTokenRejectsEmptyBalance() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Subscription subscription = Subscription.createFree(
                UUID.randomUUID(),
                SubscriptionPlanCode.FREE,
                0,
                now,
                Instant.parse("2026-02-01T00:00:00Z"),
                now
        );

        assertThrows(InsufficientTokensException.class, () -> subscription.consumeChatToken(now));
    }

    @Test
    void refundChatTokensCapsAtMonthlyLimit() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Subscription subscription = Subscription.createFree(
                UUID.randomUUID(),
                SubscriptionPlanCode.FREE,
                3,
                now,
                Instant.parse("2026-02-01T00:00:00Z"),
                now
        );
        subscription.consumeChatToken(Instant.parse("2026-01-01T00:01:00Z"));

        int delta = subscription.refundChatTokens(3, Instant.parse("2026-01-01T00:02:00Z"));

        assertEquals(1, delta);
        assertEquals(3, subscription.getRemainingTokens());
    }

    @Test
    void syncGatewaySubscriptionPreservesUsedTokensOnPlanChange() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Subscription subscription = Subscription.createFree(
                UUID.randomUUID(),
                SubscriptionPlanCode.FREE,
                50,
                now,
                Instant.parse("2026-02-01T00:00:00Z"),
                now
        );
        subscription.consumeChatToken(Instant.parse("2026-01-01T00:01:00Z"));

        subscription.syncGatewaySubscription(
                SubscriptionPlanCode.BASIC,
                SubscriptionStatus.ACTIVE,
                "customer",
                "subscription",
                now,
                Instant.parse("2026-02-01T00:00:00Z"),
                false,
                500,
                false,
                Instant.parse("2026-01-01T00:02:00Z")
        );

        assertEquals(SubscriptionPlanCode.BASIC, subscription.getPlanCode());
        assertEquals(PaymentProvider.MERCADO_PAGO, subscription.getProvider());
        assertEquals(499, subscription.getRemainingTokens());
    }
}
