package com.legalfam.backend.payment.application.port.in;

import java.util.UUID;

public interface IPaymentProvisioningUseCase {
    void provisionFreeSubscription(UUID userId);
}
