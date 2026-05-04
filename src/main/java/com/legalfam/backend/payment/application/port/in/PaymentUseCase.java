package com.legalfam.backend.payment.application.port.in;

import com.legalfam.backend.payment.application.dto.CreateCheckoutSessionRequest;
import com.legalfam.backend.payment.application.dto.CreateCheckoutSessionResponse;
import com.legalfam.backend.payment.application.dto.PaymentPlanResponse;
import com.legalfam.backend.payment.application.dto.PaymentSubscriptionResponse;
import java.util.List;
import java.util.UUID;

public interface PaymentUseCase {
    List<PaymentPlanResponse> listPlans(UUID userId);

    PaymentSubscriptionResponse getSubscription(UUID userId);

    CreateCheckoutSessionResponse createCheckoutSession(UUID userId, CreateCheckoutSessionRequest request);

    void cancelSubscription(UUID userId);

    void handleWebhook(String payload, String signatureHeader);
}
