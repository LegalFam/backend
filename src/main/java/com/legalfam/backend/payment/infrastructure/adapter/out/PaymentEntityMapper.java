package com.legalfam.backend.payment.infrastructure.adapter.out;

import com.legalfam.backend.payment.domain.model.PaymentProvider;
import com.legalfam.backend.payment.domain.model.Subscription;
import com.legalfam.backend.payment.domain.model.SubscriptionPlanCode;
import com.legalfam.backend.payment.domain.model.SubscriptionStatus;
import com.legalfam.backend.payment.domain.model.TokenTransaction;
import com.legalfam.backend.payment.domain.model.TokenTransactionType;
import com.legalfam.backend.payment.infrastructure.persistence.entity.SubscriptionEntity;
import com.legalfam.backend.payment.infrastructure.persistence.entity.TokenTransactionEntity;

final class PaymentEntityMapper {

    private PaymentEntityMapper() {
    }

    static Subscription toDomain(SubscriptionEntity entity) {
        return Subscription.restore(
                entity.getId(),
                entity.getUserId(),
                SubscriptionPlanCode.valueOf(entity.getPlanCode()),
                SubscriptionStatus.valueOf(entity.getStatus()),
                PaymentProvider.valueOf(entity.getProvider()),
                entity.getGatewayCustomerId(),
                entity.getGatewaySubscriptionId(),
                entity.getCurrentPeriodStart(),
                entity.getCurrentPeriodEnd(),
                entity.isCancelAtPeriodEnd(),
                entity.getMonthlyTokenLimit(),
                entity.getRemainingTokens(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    static SubscriptionEntity toEntity(Subscription domain) {
        SubscriptionEntity entity = new SubscriptionEntity();
        entity.setId(domain.getId());
        entity.setUserId(domain.getUserId());
        entity.setPlanCode(domain.getPlanCode().name());
        entity.setStatus(domain.getStatus().name());
        entity.setProvider(domain.getProvider().name());
        entity.setGatewayCustomerId(domain.getGatewayCustomerId());
        entity.setGatewaySubscriptionId(domain.getGatewaySubscriptionId());
        entity.setCurrentPeriodStart(domain.getCurrentPeriodStart());
        entity.setCurrentPeriodEnd(domain.getCurrentPeriodEnd());
        entity.setCancelAtPeriodEnd(domain.isCancelAtPeriodEnd());
        entity.setMonthlyTokenLimit(domain.getMonthlyTokenLimit());
        entity.setRemainingTokens(domain.getRemainingTokens());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }

    static TokenTransaction toDomain(TokenTransactionEntity entity) {
        return TokenTransaction.restore(
                entity.getId(),
                entity.getSubscriptionId(),
                entity.getUserId(),
                entity.getChatMessageId(),
                TokenTransactionType.valueOf(entity.getType()),
                entity.getTokenDelta(),
                entity.getDescription(),
                entity.getCreatedAt()
        );
    }

    static TokenTransactionEntity toEntity(TokenTransaction domain) {
        TokenTransactionEntity entity = new TokenTransactionEntity();
        entity.setId(domain.getId());
        entity.setSubscriptionId(domain.getSubscriptionId());
        entity.setUserId(domain.getUserId());
        entity.setChatMessageId(domain.getChatMessageId());
        entity.setType(domain.getType().name());
        entity.setTokenDelta(domain.getTokenDelta());
        entity.setDescription(domain.getDescription());
        entity.setCreatedAt(domain.getCreatedAt());
        return entity;
    }
}
