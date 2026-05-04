package com.legalfam.backend.payment.application.port.out;

import com.legalfam.backend.payment.domain.model.Subscription;
import com.legalfam.backend.payment.domain.model.TokenTransaction;
import com.legalfam.backend.payment.domain.model.TokenTransactionType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PaymentPersistencePort {
    Optional<Subscription> findSubscriptionById(UUID subscriptionId);

    Optional<Subscription> findSubscriptionByUserId(UUID userId);

    Optional<Subscription> findSubscriptionByGatewayCustomerId(String gatewayCustomerId);

    Optional<Subscription> findSubscriptionByGatewaySubscriptionId(String gatewaySubscriptionId);

    Subscription saveSubscription(Subscription subscription);

    TokenTransaction saveTokenTransaction(TokenTransaction tokenTransaction);

    Optional<TokenTransaction> findTokenTransactionByChatMessageIdAndType(UUID chatMessageId, TokenTransactionType type);

    boolean existsTokenTransactionByChatMessageIdAndType(UUID chatMessageId, TokenTransactionType type);

    boolean existsProcessedWebhookEvent(String eventId);

    void saveProcessedWebhookEvent(String eventId, String eventType, Instant processedAt);
}
