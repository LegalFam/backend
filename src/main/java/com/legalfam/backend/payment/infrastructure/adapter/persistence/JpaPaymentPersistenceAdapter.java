package com.legalfam.backend.payment.infrastructure.adapter.persistence;

import com.legalfam.backend.payment.application.port.out.PaymentPersistencePort;
import com.legalfam.backend.payment.domain.model.Subscription;
import com.legalfam.backend.payment.domain.model.TokenTransaction;
import com.legalfam.backend.payment.domain.model.TokenTransactionType;
import com.legalfam.backend.payment.infrastructure.persistence.PaymentWebhookEventRepository;
import com.legalfam.backend.payment.infrastructure.persistence.SubscriptionRepository;
import com.legalfam.backend.payment.infrastructure.persistence.TokenTransactionRepository;
import com.legalfam.backend.payment.infrastructure.persistence.entity.PaymentWebhookEventEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaPaymentPersistenceAdapter implements PaymentPersistencePort {

    private final SubscriptionRepository subscriptionRepository;
    private final TokenTransactionRepository tokenTransactionRepository;
    private final PaymentWebhookEventRepository paymentWebhookEventRepository;

    public JpaPaymentPersistenceAdapter(
            SubscriptionRepository subscriptionRepository,
            TokenTransactionRepository tokenTransactionRepository,
            PaymentWebhookEventRepository paymentWebhookEventRepository
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.tokenTransactionRepository = tokenTransactionRepository;
        this.paymentWebhookEventRepository = paymentWebhookEventRepository;
    }

    @Override
    public Optional<Subscription> findSubscriptionById(UUID subscriptionId) {
        return subscriptionRepository.findById(subscriptionId).map(PaymentEntityMapper::toDomain);
    }

    @Override
    public Optional<Subscription> findSubscriptionByUserId(UUID userId) {
        return subscriptionRepository.findByUserId(userId).map(PaymentEntityMapper::toDomain);
    }

    @Override
    public Optional<Subscription> findSubscriptionByGatewayCustomerId(String gatewayCustomerId) {
        return subscriptionRepository.findByGatewayCustomerId(gatewayCustomerId).map(PaymentEntityMapper::toDomain);
    }

    @Override
    public Optional<Subscription> findSubscriptionByGatewaySubscriptionId(String gatewaySubscriptionId) {
        return subscriptionRepository.findByGatewaySubscriptionId(gatewaySubscriptionId)
                .map(PaymentEntityMapper::toDomain);
    }

    @Override
    public Subscription saveSubscription(Subscription subscription) {
        return PaymentEntityMapper.toDomain(subscriptionRepository.save(PaymentEntityMapper.toEntity(subscription)));
    }

    @Override
    public TokenTransaction saveTokenTransaction(TokenTransaction tokenTransaction) {
        return PaymentEntityMapper.toDomain(
                tokenTransactionRepository.save(PaymentEntityMapper.toEntity(tokenTransaction))
        );
    }

    @Override
    public Optional<TokenTransaction> findTokenTransactionByChatMessageIdAndType(
            UUID chatMessageId,
            TokenTransactionType type
    ) {
        return tokenTransactionRepository.findByChatMessageIdAndType(chatMessageId, type.name())
                .map(PaymentEntityMapper::toDomain);
    }

    @Override
    public boolean existsTokenTransactionByChatMessageIdAndType(UUID chatMessageId, TokenTransactionType type) {
        return tokenTransactionRepository.existsByChatMessageIdAndType(chatMessageId, type.name());
    }

    @Override
    public boolean existsProcessedWebhookEvent(String eventId) {
        return paymentWebhookEventRepository.existsByEventId(eventId);
    }

    @Override
    public void saveProcessedWebhookEvent(String eventId, String eventType, Instant processedAt) {
        PaymentWebhookEventEntity entity = new PaymentWebhookEventEntity();
        entity.setEventId(eventId);
        entity.setEventType(eventType);
        entity.setProcessedAt(processedAt);
        paymentWebhookEventRepository.save(entity);
    }
}
