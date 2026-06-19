package com.legalfam.backend.payment.infrastructure.adapter.out;

import com.legalfam.backend.payment.application.port.out.IPaymentPersistencePort;
import com.legalfam.backend.payment.domain.model.Subscription;
import com.legalfam.backend.payment.domain.model.TokenTransaction;
import com.legalfam.backend.payment.domain.model.TokenTransactionType;
import com.legalfam.backend.payment.infrastructure.persistence.IPaymentWebhookEventRepository;
import com.legalfam.backend.payment.infrastructure.persistence.ISubscriptionRepository;
import com.legalfam.backend.payment.infrastructure.persistence.ITokenTransactionRepository;
import com.legalfam.backend.payment.infrastructure.persistence.entity.PaymentWebhookEventEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class JpaPaymentPersistenceAdapter implements IPaymentPersistencePort {

    private final ISubscriptionRepository ISubscriptionRepository;
    private final ITokenTransactionRepository ITokenTransactionRepository;
    private final IPaymentWebhookEventRepository IPaymentWebhookEventRepository;

    public JpaPaymentPersistenceAdapter(
            ISubscriptionRepository ISubscriptionRepository,
            ITokenTransactionRepository ITokenTransactionRepository,
            IPaymentWebhookEventRepository IPaymentWebhookEventRepository
    ) {
        this.ISubscriptionRepository = ISubscriptionRepository;
        this.ITokenTransactionRepository = ITokenTransactionRepository;
        this.IPaymentWebhookEventRepository = IPaymentWebhookEventRepository;
    }

    @Override
    public Optional<Subscription> findSubscriptionByUserId(UUID userId) {
        return ISubscriptionRepository.findByUserId(userId).map(PaymentEntityMapper::toDomain);
    }

    @Override
    public Optional<Subscription> findSubscriptionByUserIdForUpdate(UUID userId) {
        return ISubscriptionRepository.findByUserIdForUpdate(userId).map(PaymentEntityMapper::toDomain);
    }

    @Override
    public Optional<Subscription> findSubscriptionByGatewayCustomerId(String gatewayCustomerId) {
        return ISubscriptionRepository.findByGatewayCustomerId(gatewayCustomerId).map(PaymentEntityMapper::toDomain);
    }

    @Override
    public Optional<Subscription> findSubscriptionByGatewaySubscriptionId(String gatewaySubscriptionId) {
        return ISubscriptionRepository.findByGatewaySubscriptionId(gatewaySubscriptionId)
                .map(PaymentEntityMapper::toDomain);
    }

    @Override
    public Subscription saveSubscription(Subscription subscription) {
        return PaymentEntityMapper.toDomain(ISubscriptionRepository.save(PaymentEntityMapper.toEntity(subscription)));
    }

    @Override
    public TokenTransaction saveTokenTransaction(TokenTransaction tokenTransaction) {
        return PaymentEntityMapper.toDomain(
                ITokenTransactionRepository.save(PaymentEntityMapper.toEntity(tokenTransaction))
        );
    }

    @Override
    public boolean existsTokenTransactionByChatMessageIdAndType(UUID chatMessageId, TokenTransactionType type) {
        return ITokenTransactionRepository.existsByChatMessageIdAndType(chatMessageId, type.name());
    }

    @Override
    public boolean tryRecordProcessedWebhookEvent(String eventId, String eventType, Instant processedAt) {
        PaymentWebhookEventEntity entity = new PaymentWebhookEventEntity();
        entity.setEventId(eventId);
        entity.setEventType(eventType);
        entity.setProcessedAt(processedAt);
        try {
            IPaymentWebhookEventRepository.saveAndFlush(entity);
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }
}
