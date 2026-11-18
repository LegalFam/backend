package com.legalfam.backend.payment.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.legalfam.backend.payment.infrastructure.persistence.entity.SubscriptionEntity;
import com.legalfam.backend.payment.infrastructure.persistence.entity.TokenTransactionEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentEntityMapperTest {

    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

    @Test
    void roundTripsSubscription() {
        SubscriptionEntity entity = new SubscriptionEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(UUID.randomUUID());
        entity.setPlanCode("PREMIUM");
        entity.setStatus("ACTIVE");
        entity.setProvider("MERCADO_PAGO");
        entity.setGatewayCustomerId("cus_1");
        entity.setGatewaySubscriptionId("sub_1");
        entity.setCurrentPeriodStart(NOW);
        entity.setCurrentPeriodEnd(NOW.plusSeconds(2_592_000));
        entity.setCancelAtPeriodEnd(true);
        entity.setMonthlyTokenLimit(200);
        entity.setRemainingTokens(150);
        entity.setCreatedAt(NOW);
        entity.setUpdatedAt(NOW);

        assertThat(PaymentEntityMapper.toEntity(PaymentEntityMapper.toDomain(entity)))
                .usingRecursiveComparison()
                .isEqualTo(entity);
    }

    @Test
    void roundTripsTokenTransaction() {
        TokenTransactionEntity entity = new TokenTransactionEntity();
        entity.setId(UUID.randomUUID());
        entity.setSubscriptionId(UUID.randomUUID());
        entity.setUserId(UUID.randomUUID());
        entity.setChatMessageId(UUID.randomUUID());
        entity.setType("CHAT_CONSUMPTION");
        entity.setTokenDelta(-3);
        entity.setDescription("Consulta");
        entity.setCreatedAt(NOW);

        assertThat(PaymentEntityMapper.toEntity(PaymentEntityMapper.toDomain(entity)))
                .usingRecursiveComparison()
                .isEqualTo(entity);
    }
}
