package com.legalfam.backend.payment.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.legalfam.backend.payment.domain.exception.InsufficientTokensException;
import com.legalfam.backend.payment.domain.exception.InvalidPaymentRequestException;
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

    @Test
    void shouldRefreshFreePeriodOnlyWhenFreePeriodHasEnded() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Subscription subscription = Subscription.createFree(
                UUID.randomUUID(),
                SubscriptionPlanCode.FREE,
                50,
                now,
                Instant.parse("2026-02-01T00:00:00Z"),
                now
        );

        assertFalse(subscription.shouldRefreshFreePeriodAt(Instant.parse("2026-01-15T00:00:00Z")));
        assertTrue(subscription.shouldRefreshFreePeriodAt(Instant.parse("2026-02-01T00:00:00Z")));
    }

    @Test
    void assertCheckoutAllowedRejectsSameActiveGatewayPlan() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Subscription subscription = Subscription.restore(
                UUID.randomUUID(),
                UUID.randomUUID(),
                SubscriptionPlanCode.BASIC,
                SubscriptionStatus.ACTIVE,
                PaymentProvider.MERCADO_PAGO,
                "customer",
                "subscription",
                now,
                Instant.parse("2026-02-01T00:00:00Z"),
                false,
                500,
                500,
                now,
                now
        );

        assertThrows(
                InvalidPaymentRequestException.class,
                () -> subscription.assertCheckoutAllowedFor(SubscriptionPlanCode.BASIC)
        );
    }

    @Test
    void assertCheckoutAllowedRejectsChangingActiveGatewayPlanBeforeCanceling() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Subscription subscription = Subscription.restore(
                UUID.randomUUID(),
                UUID.randomUUID(),
                SubscriptionPlanCode.BASIC,
                SubscriptionStatus.ACTIVE,
                PaymentProvider.MERCADO_PAGO,
                "customer",
                "subscription",
                now,
                Instant.parse("2026-02-01T00:00:00Z"),
                false,
                500,
                500,
                now,
                now
        );

        assertThrows(
                InvalidPaymentRequestException.class,
                () -> subscription.assertCheckoutAllowedFor(SubscriptionPlanCode.PREMIUM)
        );
    }
}
