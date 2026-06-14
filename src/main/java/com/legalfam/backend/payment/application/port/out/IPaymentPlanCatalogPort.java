package com.legalfam.backend.payment.application.port.out;

import com.legalfam.backend.payment.application.dto.PaymentPlanDefinition;
import com.legalfam.backend.payment.domain.model.SubscriptionPlanCode;
import java.util.List;

public interface IPaymentPlanCatalogPort {
    List<PaymentPlanDefinition> listPlans();

    PaymentPlanDefinition getFreePlan();

    PaymentPlanDefinition getPlan(SubscriptionPlanCode code);

    PaymentPlanDefinition getPaidPlanOrThrow(String code);
}
