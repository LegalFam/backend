package com.legalfam.backend.payment.application.port.in;

import java.util.UUID;

public interface PaymentProvisioningUseCase {
    void provisionFreeSubscription(UUID userId);
}
