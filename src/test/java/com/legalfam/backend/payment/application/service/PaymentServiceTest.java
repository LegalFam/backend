package com.legalfam.backend.payment.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.legalfam.backend.common.identity.application.port.out.IUserIdentityPort;
import com.legalfam.backend.payment.application.port.out.IPaymentGatewayPort;
import com.legalfam.backend.payment.application.port.out.IPaymentPersistencePort;
import com.legalfam.backend.payment.application.port.out.IPaymentPlanCatalogPort;
import com.legalfam.backend.payment.domain.model.PaymentProvider;
import com.legalfam.backend.payment.domain.model.Subscription;
import com.legalfam.backend.payment.domain.model.SubscriptionPlanCode;
import com.legalfam.backend.payment.domain.model.SubscriptionStatus;
import com.legalfam.backend.payment.domain.model.TokenTransaction;
import com.legalfam.backend.payment.domain.model.TokenTransactionType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
        UUID subscriptionId = UUID.randomUUID();
        UUID chatMessageId = UUID.randomUUID();
        when(paymentPersistencePort.existsTokenTransactionByChatMessageIdAndType(
                chatMessageId,
                TokenTransactionType.CHAT_CONSUMPTION
        )).thenReturn(true);

        paymentService.consumeChatTokensForAssistantResult(userId, chatMessageId, 3);

        verify(paymentPersistencePort, never()).saveTokenTransaction(any());
    }

    @Test
    void refundChatTokenRefundsConsumption() {
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID chatMessageId = UUID.randomUUID();
        Subscription subscription = subscription(subscriptionId, userId, 0, 5);
        TokenTransaction consumption = tokenTransaction(subscriptionId, userId, chatMessageId, -3);
        when(paymentPersistencePort.findTokenTransactionByChatMessageIdAndType(
                chatMessageId,
                TokenTransactionType.CHAT_CONSUMPTION
        )).thenReturn(Optional.of(consumption));
        when(paymentPersistencePort.findSubscriptionByIdForUpdate(subscriptionId)).thenReturn(Optional.of(subscription));
        when(paymentPersistencePort.saveSubscription(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.refundChatToken(chatMessageId);

        assertEquals(3, subscription.getRemainingTokens());
        ArgumentCaptor<TokenTransaction> transactionCaptor = ArgumentCaptor.forClass(TokenTransaction.class);
        verify(paymentPersistencePort).saveTokenTransaction(transactionCaptor.capture());
        TokenTransaction transaction = transactionCaptor.getValue();
        assertEquals(TokenTransactionType.CHAT_REFUND, transaction.getType());
        assertEquals(3, transaction.getTokenDelta());
    }

    private Subscription subscription(UUID subscriptionId, UUID userId, int remainingTokens, int monthlyTokenLimit) {
        return Subscription.restore(
                subscriptionId,
                userId,
                SubscriptionPlanCode.FREE,
                SubscriptionStatus.ACTIVE,
                PaymentProvider.FREE,
                null,
                null,
                NOW,
                Instant.parse("2026-02-01T00:00:00Z"),
                false,
                monthlyTokenLimit,
                remainingTokens,
                NOW,
                NOW
        );
    }

    private TokenTransaction tokenTransaction(UUID subscriptionId, UUID userId, UUID chatMessageId, int tokenDelta) {
        TokenTransaction transaction = new TokenTransaction();
        transaction.setSubscriptionId(subscriptionId);
        transaction.setUserId(userId);
        transaction.setChatMessageId(chatMessageId);
        transaction.setType(TokenTransactionType.CHAT_CONSUMPTION);
        transaction.setTokenDelta(tokenDelta);
        transaction.setDescription("test");
        transaction.setCreatedAt(NOW);
        return transaction;
    }
}
