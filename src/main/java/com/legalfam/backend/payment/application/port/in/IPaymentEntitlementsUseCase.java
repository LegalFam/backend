package com.legalfam.backend.payment.application.port.in;

import com.legalfam.backend.payment.application.dto.PaymentEntitlements;
import java.util.UUID;

public interface IPaymentEntitlementsUseCase {
    PaymentEntitlements resolveEntitlements(UUID userId);
}
