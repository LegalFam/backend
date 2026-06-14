package com.legalfam.backend.payment.infrastructure.persistence;

import com.legalfam.backend.payment.infrastructure.persistence.entity.SubscriptionEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ISubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {
    Optional<SubscriptionEntity> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SubscriptionEntity s where s.id = :subscriptionId")
    Optional<SubscriptionEntity> findByIdForUpdate(@Param("subscriptionId") UUID subscriptionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SubscriptionEntity s where s.userId = :userId")
    Optional<SubscriptionEntity> findByUserIdForUpdate(@Param("userId") UUID userId);

    Optional<SubscriptionEntity> findByGatewayCustomerId(String gatewayCustomerId);

    Optional<SubscriptionEntity> findByGatewaySubscriptionId(String gatewaySubscriptionId);
}
