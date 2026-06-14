package com.legalfam.backend.payment.infrastructure.persistence;

import com.legalfam.backend.payment.infrastructure.persistence.entity.PaymentWebhookEventEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IPaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEventEntity, UUID> {
    boolean existsByEventId(String eventId);
}
