package com.legalfam.backend.payment.infrastructure.adapter.out;

import com.legalfam.backend.payment.application.dto.PaymentPlanDefinition;
import com.legalfam.backend.payment.application.port.out.IPaymentPlanCatalogPort;
import com.legalfam.backend.payment.domain.exception.InvalidPaymentRequestException;
import com.legalfam.backend.payment.domain.model.SubscriptionPlanCode;
import com.legalfam.backend.payment.infrastructure.config.PaymentPlanProperties;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PaymentPlanCatalogAdapter implements IPaymentPlanCatalogPort {

    private final List<PaymentPlanDefinition> plans;
    private final Map<SubscriptionPlanCode, PaymentPlanDefinition> plansByCode;

    public PaymentPlanCatalogAdapter(PaymentPlanProperties properties) {
        this.plans = List.of(
                plan(SubscriptionPlanCode.FREE, properties.free()),
                plan(SubscriptionPlanCode.BASIC, properties.basic()),
                plan(SubscriptionPlanCode.PREMIUM, properties.premium())
        );
        this.plansByCode = plans.stream().collect(Collectors.toMap(PaymentPlanDefinition::code, plan -> plan));
    }

    @Override
    public List<PaymentPlanDefinition> listPlans() {
        return plans;
    }

    @Override
    public PaymentPlanDefinition getFreePlan() {
        return plansByCode.get(SubscriptionPlanCode.FREE);
    }

    @Override
    public PaymentPlanDefinition getPlan(SubscriptionPlanCode code) {
        return plansByCode.get(code);
    }

    @Override
    public PaymentPlanDefinition getPaidPlanOrThrow(String code) {
        SubscriptionPlanCode normalizedCode = parseCode(code);
        PaymentPlanDefinition plan = plansByCode.get(normalizedCode);
        if (plan == null || plan.isFree()) {
            throw new InvalidPaymentRequestException("Paid plan is required");
        }
        if (!plan.isPurchasable()) {
            throw new InvalidPaymentRequestException("Selected plan is not configured for checkout");
        }
        return plan;
    }

    private SubscriptionPlanCode parseCode(String code) {
        if (code == null || code.isBlank()) {
            throw new InvalidPaymentRequestException("Plan code is required");
        }
        try {
            return SubscriptionPlanCode.valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidPaymentRequestException("Plan code is invalid");
        }
    }

    private PaymentPlanDefinition plan(SubscriptionPlanCode code, PaymentPlanProperties.Plan properties) {
        String displayName = properties.displayName();
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalStateException("Payment plan display name is required for " + code.name());
        }
        int monthlyTokenLimit = properties.tokens();
        int monthlyPriceCents = properties.monthlyPriceCents();
        if (monthlyTokenLimit < 0) {
            throw new IllegalStateException("Payment plan token limit must be non-negative for " + code.name());
        }
        if (monthlyPriceCents < 0) {
            throw new IllegalStateException("Payment plan price must be non-negative for " + code.name());
        }
        return new PaymentPlanDefinition(
                code,
                displayName.trim(),
                defaultString(properties.description()),
                monthlyTokenLimit,
                monthlyPriceCents,
                defaultCurrency(properties.currency())
        );
    }

    private String defaultString(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultCurrency(String value) {
        if (value == null || value.isBlank()) {
            return "pen";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
