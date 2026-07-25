package com.legalfam.backend.payment.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.legalfam.backend.common.identity.UserIdentity;
import com.legalfam.backend.common.identity.application.port.out.IUserIdentityPort;
import com.legalfam.backend.payment.application.dto.CreateCheckoutSessionRequest;
import com.legalfam.backend.payment.application.dto.CreateCheckoutSessionResponse;
import com.legalfam.backend.payment.application.dto.PaymentPlanDefinition;
import com.legalfam.backend.payment.application.dto.PaymentPlanResponse;
import com.legalfam.backend.payment.application.dto.PaymentWebhookNotification;
import com.legalfam.backend.payment.application.port.out.IPaymentGatewayPort;
import com.legalfam.backend.payment.application.port.out.IPaymentPersistencePort;
import com.legalfam.backend.payment.application.port.out.IPaymentPlanCatalogPort;
import com.legalfam.backend.payment.domain.exception.InvalidPaymentRequestException;
import com.legalfam.backend.payment.domain.model.PaymentProvider;
import com.legalfam.backend.payment.domain.model.Subscription;
import com.legalfam.backend.payment.domain.model.SubscriptionPlanCode;
import com.legalfam.backend.payment.domain.model.SubscriptionStatus;
import com.legalfam.backend.payment.domain.model.TokenTransaction;
import com.legalfam.backend.payment.domain.model.TokenTransactionType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private IPaymentPersistencePort paymentPersistencePort;

    @Mock
    private IPaymentGatewayPort paymentGatewayPort;

    @Mock
    private IPaymentPlanCatalogPort paymentPlanCatalogPort;

    @Mock
    private IUserIdentityPort userIdentityPort;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentPersistencePort,
                paymentGatewayPort,
                paymentPlanCatalogPort,
                userIdentityPort,
                new PaymentCheckoutProperties("", ""),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void consumeChatTokensForAssistantResultCreatesParserOnlyConsumption() {
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID chatMessageId = UUID.randomUUID();
        Subscription subscription = subscription(subscriptionId, userId, 5, 5);
        when(paymentPersistencePort.findSubscriptionByUserIdForUpdate(userId)).thenReturn(Optional.of(subscription));
        when(paymentPersistencePort.saveSubscription(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.consumeChatTokensForAssistantResult(userId, chatMessageId, 1);

        assertEquals(4, subscription.getRemainingTokens());
        ArgumentCaptor<TokenTransaction> transactionCaptor = ArgumentCaptor.forClass(TokenTransaction.class);
        verify(paymentPersistencePort).saveTokenTransaction(transactionCaptor.capture());
        TokenTransaction transaction = transactionCaptor.getValue();
        assertEquals(TokenTransactionType.CHAT_CONSUMPTION, transaction.getType());
        assertEquals(-1, transaction.getTokenDelta());
        assertEquals(chatMessageId, transaction.getChatMessageId());
    }

    @Test
    void consumeChatTokensForAssistantResultCreatesRagConsumption() {
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID chatMessageId = UUID.randomUUID();
        Subscription subscription = subscription(subscriptionId, userId, 5, 5);
        when(paymentPersistencePort.findSubscriptionByUserIdForUpdate(userId)).thenReturn(Optional.of(subscription));
        when(paymentPersistencePort.saveSubscription(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.consumeChatTokensForAssistantResult(userId, chatMessageId, 3);

        assertEquals(2, subscription.getRemainingTokens());
        ArgumentCaptor<TokenTransaction> transactionCaptor = ArgumentCaptor.forClass(TokenTransaction.class);
        verify(paymentPersistencePort).saveTokenTransaction(transactionCaptor.capture());
        assertEquals(TokenTransactionType.CHAT_CONSUMPTION, transactionCaptor.getValue().getType());
        assertEquals(-3, transactionCaptor.getValue().getTokenDelta());
    }

    @Test
    void consumeChatTokensForAssistantResultConsumesOnlyRemainingTokens() {
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID chatMessageId = UUID.randomUUID();
        Subscription subscription = subscription(subscriptionId, userId, 2, 5);
        when(paymentPersistencePort.findSubscriptionByUserIdForUpdate(userId)).thenReturn(Optional.of(subscription));
        when(paymentPersistencePort.saveSubscription(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.consumeChatTokensForAssistantResult(userId, chatMessageId, 3);

        assertEquals(0, subscription.getRemainingTokens());
        ArgumentCaptor<TokenTransaction> transactionCaptor = ArgumentCaptor.forClass(TokenTransaction.class);
        verify(paymentPersistencePort).saveTokenTransaction(transactionCaptor.capture());
        assertEquals(-2, transactionCaptor.getValue().getTokenDelta());
    }

    @Test
    void consumeChatTokensForAssistantResultIsIdempotentWhenConsumptionAlreadyExists() {
        UUID userId = UUID.randomUUID();
        UUID chatMessageId = UUID.randomUUID();
        when(paymentPersistencePort.existsTokenTransactionByChatMessageIdAndType(
                chatMessageId,
                TokenTransactionType.CHAT_CONSUMPTION
        )).thenReturn(true);

        paymentService.consumeChatTokensForAssistantResult(userId, chatMessageId, 3);

        verify(paymentPersistencePort, never()).saveTokenTransaction(any());
    }

    @Test
    void listPlansMarksAuthenticatedUsersCurrentPlan() {
        UUID userId = UUID.randomUUID();
        when(paymentPersistencePort.findSubscriptionByUserId(userId)).thenReturn(Optional.of(subscription(
                UUID.randomUUID(),
                userId,
                400,
                500,
                SubscriptionPlanCode.BASIC,
                PaymentProvider.MERCADO_PAGO
        )));
        when(paymentPlanCatalogPort.listPlans()).thenReturn(List.of(
                plan(SubscriptionPlanCode.FREE, 50, 0),
                plan(SubscriptionPlanCode.BASIC, 500, 1499),
                plan(SubscriptionPlanCode.PREMIUM, 1500, 2999)
        ));

        List<PaymentPlanResponse> response = paymentService.listPlans(userId);

        assertFalse(response.get(0).currentPlan());
        assertTrue(response.get(1).currentPlan());
        assertFalse(response.get(2).currentPlan());
        assertTrue(response.get(0).purchasable());
        assertTrue(response.get(1).purchasable());
        assertTrue(response.get(2).purchasable());
    }

    @Test
    void createCheckoutSessionUsesTrimmedCustomUrlsAndGatewayResponse() {
        UUID userId = UUID.randomUUID();
        PaymentPlanDefinition basicPlan = plan(SubscriptionPlanCode.BASIC, 500, 1499);
        Subscription subscription = subscription(UUID.randomUUID(), userId, 50, 50);
        when(paymentPlanCatalogPort.getPaidPlanOrThrow("BASIC")).thenReturn(basicPlan);
        when(userIdentityPort.findUserIdentityById(userId)).thenReturn(Optional.of(new UserIdentity(userId, "user@example.com")));
        when(paymentPersistencePort.findSubscriptionByUserId(userId)).thenReturn(Optional.of(subscription));
        when(paymentGatewayPort.createCheckoutSession(
                eq(userId),
                eq("user@example.com"),
                eq(basicPlan),
                eq("https://app.example.com/success"),
                eq("https://app.example.com/cancel")
        )).thenReturn("https://checkout.example.com/session");

        CreateCheckoutSessionResponse response = paymentService.createCheckoutSession(
                userId,
                new CreateCheckoutSessionRequest(
                        "BASIC",
                        " https://app.example.com/success ",
                        " https://app.example.com/cancel "
                )
        );

        assertEquals("https://checkout.example.com/session", response.url());
    }

    @Test
    void createCheckoutSessionRejectsMissingRequestBeforeDependencyWork() {
        UUID userId = UUID.randomUUID();

        InvalidPaymentRequestException exception = assertThrows(
                InvalidPaymentRequestException.class,
                () -> paymentService.createCheckoutSession(userId, null)
        );

        assertEquals("checkout_request_required", exception.error().code());
        verifyNoInteractions(paymentPlanCatalogPort, userIdentityPort, paymentPersistencePort, paymentGatewayPort);
    }

    @Test
    void handleWebhookSkipsDuplicateRecordedEvent() {
        PaymentWebhookNotification notification = new PaymentWebhookNotification(
                "evt-1",
                "subscription.updated",
                "customer-1",
                "subscription-1",
                UUID.randomUUID(),
                "BASIC",
                "active",
                false,
                NOW,
                Instant.parse("2026-02-01T00:00:00Z"),
                false
        );
        when(paymentGatewayPort.parseVerifiedWebhook("{\"id\":\"evt-1\"}", "sig", "req", "evt-1"))
                .thenReturn(notification);
        when(paymentPersistencePort.tryRecordProcessedWebhookEvent("evt-1", "subscription.updated", NOW))
                .thenReturn(false);

        paymentService.handleWebhook("{\"id\":\"evt-1\"}", "sig", "req", "evt-1");

        verify(paymentPersistencePort, never()).findSubscriptionByGatewaySubscriptionId(any());
        verify(paymentPersistencePort, never()).saveSubscription(any());
        verify(paymentPersistencePort, never()).saveTokenTransaction(any());
    }

    @Test
    void cancelSubscriptionKeepsPlanAndTokensUntilPaidPeriodEnds() {
        UUID userId = UUID.randomUUID();
        Subscription paid = subscription(
                UUID.randomUUID(), userId, 437, 500,
                SubscriptionPlanCode.BASIC, PaymentProvider.MERCADO_PAGO
        );
        when(paymentPersistencePort.findSubscriptionByUserId(userId)).thenReturn(Optional.of(paid));
        when(paymentPersistencePort.saveSubscription(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.cancelSubscription(userId);

        verify(paymentGatewayPort).cancelSubscription("gateway-subscription-id");

        ArgumentCaptor<Subscription> saved = ArgumentCaptor.forClass(Subscription.class);
        verify(paymentPersistencePort).saveSubscription(saved.capture());
        Subscription result = saved.getValue();
        assertEquals(SubscriptionPlanCode.BASIC, result.getPlanCode());
        assertEquals(437, result.getRemainingTokens());
        assertEquals(500, result.getMonthlyTokenLimit());
        assertEquals(Instant.parse("2026-02-01T00:00:00Z"), result.getCurrentPeriodEnd());
        assertTrue(result.isCancelAtPeriodEnd());

        // No downgrade happened, so no free-plan reallocation should be recorded.
        verify(paymentPersistencePort, never()).saveTokenTransaction(any());
    }

    @Test
    void cancelSubscriptionRejectsWhenCancellationIsAlreadyScheduled() {
        UUID userId = UUID.randomUUID();
        Subscription pending = pendingCancellation(userId, 437, Instant.parse("2026-02-01T00:00:00Z"));
        when(paymentPersistencePort.findSubscriptionByUserId(userId)).thenReturn(Optional.of(pending));

        assertThrows(InvalidPaymentRequestException.class, () -> paymentService.cancelSubscription(userId));

        verifyNoInteractions(paymentGatewayPort);
        verify(paymentPersistencePort, never()).saveSubscription(any());
    }

    @Test
    void getSubscriptionKeepsPaidTokensWhileCancellationIsPending() {
        UUID userId = UUID.randomUUID();
        Subscription pending = pendingCancellation(userId, 437, Instant.parse("2026-02-01T00:00:00Z"));
        when(paymentPersistencePort.findSubscriptionByUserId(userId)).thenReturn(Optional.of(pending));

        var response = paymentService.getSubscription(userId);

        assertEquals("BASIC", response.planCode());
        assertEquals(437, response.remainingTokens());
        assertTrue(response.cancelAtPeriodEnd());
        verify(paymentPersistencePort, never()).saveSubscription(any());
    }

    @Test
    void getSubscriptionDowngradesToFreeOncePaidPeriodEnded() {
        UUID userId = UUID.randomUUID();
        Subscription expired = pendingCancellation(userId, 437, NOW.minusSeconds(1));
        when(paymentPersistencePort.findSubscriptionByUserId(userId)).thenReturn(Optional.of(expired));
        when(paymentPlanCatalogPort.getFreePlan()).thenReturn(plan(SubscriptionPlanCode.FREE, 50, 0));
        when(paymentPersistencePort.saveSubscription(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = paymentService.getSubscription(userId);

        assertEquals("FREE", response.planCode());
        assertEquals("FREE", response.provider());
        assertEquals(50, response.remainingTokens());
        assertFalse(response.cancelAtPeriodEnd());
    }

    @Test
    void subscriptionDeletedWebhookDoesNotWipeTokensWhilePaidPeriodRemains() {
        UUID userId = UUID.randomUUID();
        Subscription paid = subscription(
                UUID.randomUUID(), userId, 437, 500,
                SubscriptionPlanCode.BASIC, PaymentProvider.MERCADO_PAGO
        );
        PaymentWebhookNotification canceled = new PaymentWebhookNotification(
                "evt-cancel",
                "subscription.updated",
                "customer-1",
                "gateway-subscription-id",
                userId,
                "BASIC",
                "cancelled",
                true,
                NOW,
                Instant.parse("2026-02-01T00:00:00Z"),
                false
        );
        when(paymentGatewayPort.parseVerifiedWebhook("{}", "sig", "req", "evt-cancel"))
                .thenReturn(canceled);
        when(paymentPersistencePort.tryRecordProcessedWebhookEvent("evt-cancel", "subscription.updated", NOW))
                .thenReturn(true);
        when(paymentPersistencePort.findSubscriptionByGatewaySubscriptionId("gateway-subscription-id"))
                .thenReturn(Optional.of(paid));
        when(paymentPersistencePort.saveSubscription(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.handleWebhook("{}", "sig", "req", "evt-cancel");

        ArgumentCaptor<Subscription> saved = ArgumentCaptor.forClass(Subscription.class);
        verify(paymentPersistencePort).saveSubscription(saved.capture());
        assertEquals(SubscriptionPlanCode.BASIC, saved.getValue().getPlanCode());
        assertEquals(437, saved.getValue().getRemainingTokens());
        assertTrue(saved.getValue().isCancelAtPeriodEnd());
    }

    private Subscription pendingCancellation(UUID userId, int remainingTokens, Instant periodEnd) {
        return Subscription.restore(
                UUID.randomUUID(),
                userId,
                SubscriptionPlanCode.BASIC,
                SubscriptionStatus.ACTIVE,
                PaymentProvider.MERCADO_PAGO,
                null,
                "gateway-subscription-id",
                NOW.minusSeconds(60),
                periodEnd,
                true,
                500,
                remainingTokens,
                NOW,
                NOW
        );
    }

    @Test
    void hasChatTokensAvailableReturnsTrueWhenActiveSubscriptionHasTokens() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = subscription(UUID.randomUUID(), userId, 3, 50);
        when(paymentPersistencePort.findSubscriptionByUserId(userId)).thenReturn(Optional.of(subscription));

        assertTrue(paymentService.hasChatTokensAvailable(userId));
    }

    @Test
    void hasChatTokensAvailableReturnsFalseWhenNoTokensRemain() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = subscription(UUID.randomUUID(), userId, 0, 50);
        when(paymentPersistencePort.findSubscriptionByUserId(userId)).thenReturn(Optional.of(subscription));

        assertFalse(paymentService.hasChatTokensAvailable(userId));
    }

    @Test
    void hasChatTokensAvailableReturnsFalseForNullUser() {
        assertFalse(paymentService.hasChatTokensAvailable(null));
        verifyNoInteractions(paymentPersistencePort);
    }

    private Subscription subscription(UUID subscriptionId, UUID userId, int remainingTokens, int monthlyTokenLimit) {
        return subscription(
                subscriptionId,
                userId,
                remainingTokens,
                monthlyTokenLimit,
                SubscriptionPlanCode.FREE,
                PaymentProvider.FREE
        );
    }

    private Subscription subscription(
            UUID subscriptionId,
            UUID userId,
            int remainingTokens,
            int monthlyTokenLimit,
            SubscriptionPlanCode planCode,
            PaymentProvider provider
    ) {
        return Subscription.restore(
                subscriptionId,
                userId,
                planCode,
                SubscriptionStatus.ACTIVE,
                provider,
                null,
                provider == PaymentProvider.MERCADO_PAGO ? "gateway-subscription-id" : null,
                NOW,
                Instant.parse("2026-02-01T00:00:00Z"),
                false,
                monthlyTokenLimit,
                remainingTokens,
                NOW,
                NOW
        );
    }

    private PaymentPlanDefinition plan(SubscriptionPlanCode code, int tokenLimit, int monthlyPriceCents) {
        return new PaymentPlanDefinition(
                code,
                code.name(),
                code.name() + " plan",
                tokenLimit,
                monthlyPriceCents,
                "pen",
                code == SubscriptionPlanCode.FREE ? 10 : 15,
                code == SubscriptionPlanCode.FREE ? 30 : null
        );
    }

}
