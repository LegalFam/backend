package com.legalfam.backend.payment.infrastructure.persistence;

import com.legalfam.backend.payment.infrastructure.persistence.entity.SubscriptionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ISubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {
    Optional<SubscriptionEntity> findByUserId(UUID userId);

    Optional<SubscriptionEntity> findByGatewayCustomerId(String gatewayCustomerId);

    Optional<SubscriptionEntity> findByGatewaySubscriptionId(String gatewaySubscriptionId);
}
