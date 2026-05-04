package com.legalfam.backend.payment.application.port.out;

import com.legalfam.backend.payment.application.dto.PaymentPlanDefinition;
import com.legalfam.backend.payment.application.dto.PaymentSubscriptionSnapshot;
import com.legalfam.backend.payment.application.dto.PaymentWebhookNotification;
import java.util.UUID;

public interface PaymentGatewayPort {
    String createCheckoutSession(
            UUID userId,
            String email,
            PaymentPlanDefinition plan,
            String successUrl
    );

    void cancelSubscription(String subscriptionId);

    PaymentWebhookNotification parseWebhook(String payload, String signatureHeader);

    PaymentSubscriptionSnapshot fetchSubscriptionSnapshot(String subscriptionId);
}
