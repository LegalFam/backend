package com.legalfam.backend.payment.application.port.out;

import com.legalfam.backend.payment.domain.model.Subscription;
import com.legalfam.backend.payment.domain.model.TokenTransaction;
import com.legalfam.backend.payment.domain.model.TokenTransactionType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IPaymentPersistencePort {
    Optional<Subscription> findSubscriptionByUserId(UUID userId);

    Optional<Subscription> findSubscriptionByUserIdForUpdate(UUID userId);

    Optional<Subscription> findSubscriptionByGatewayCustomerId(String gatewayCustomerId);

    Optional<Subscription> findSubscriptionByGatewaySubscriptionId(String gatewaySubscriptionId);

    Subscription saveSubscription(Subscription subscription);

    TokenTransaction saveTokenTransaction(TokenTransaction tokenTransaction);

    boolean existsTokenTransactionByChatMessageIdAndType(UUID chatMessageId, TokenTransactionType type);

    boolean tryRecordProcessedWebhookEvent(String eventId, String eventType, Instant processedAt);
}
