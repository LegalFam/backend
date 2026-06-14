package com.legalfam.backend.payment.infrastructure.config;

import com.legalfam.backend.payment.application.dto.PaymentPlanDefinition;
import com.legalfam.backend.payment.application.port.out.IPaymentPlanCatalogPort;
import com.legalfam.backend.payment.domain.exception.InvalidPaymentRequestException;
import com.legalfam.backend.payment.domain.model.SubscriptionPlanCode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PaymentCatalog implements IPaymentPlanCatalogPort {

    private final List<PaymentPlanDefinition> plans;
    private final Map<SubscriptionPlanCode, PaymentPlanDefinition> plansByCode;

    public PaymentCatalog(
            @Value("${app.payment.plans.free.display-name}") String freeDisplayName,
            @Value("${app.payment.plans.free.description}") String freeDescription,
            @Value("${app.payment.plans.free.tokens}") int freeTokens,
            @Value("${app.payment.plans.free.monthly-price-cents}") int freeMonthlyPriceCents,
            @Value("${app.payment.plans.free.currency}") String freeCurrency,
            @Value("${app.payment.plans.basic.display-name}") String basicDisplayName,
            @Value("${app.payment.plans.basic.description}") String basicDescription,
            @Value("${app.payment.plans.basic.tokens}") int basicTokens,
            @Value("${app.payment.plans.basic.monthly-price-cents}") int basicMonthlyPriceCents,
            @Value("${app.payment.plans.basic.currency}") String basicCurrency,
            @Value("${app.payment.plans.premium.display-name}") String premiumDisplayName,
            @Value("${app.payment.plans.premium.description}") String premiumDescription,
            @Value("${app.payment.plans.premium.tokens}") int premiumTokens,
            @Value("${app.payment.plans.premium.monthly-price-cents}") int premiumMonthlyPriceCents,
            @Value("${app.payment.plans.premium.currency}") String premiumCurrency
    ) {
        this.plans = List.of(
                plan(SubscriptionPlanCode.FREE, freeDisplayName, freeDescription, freeTokens,
                        freeMonthlyPriceCents, freeCurrency),
                plan(SubscriptionPlanCode.BASIC, basicDisplayName, basicDescription, basicTokens,
                        basicMonthlyPriceCents, basicCurrency),
                plan(SubscriptionPlanCode.PREMIUM, premiumDisplayName, premiumDescription, premiumTokens,
                        premiumMonthlyPriceCents, premiumCurrency)
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

    private PaymentPlanDefinition plan(
            SubscriptionPlanCode code,
            String displayName,
            String description,
            int monthlyTokenLimit,
            int monthlyPriceCents,
            String currency
    ) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalStateException("Payment plan display name is required for " + code.name());
        }
        if (monthlyTokenLimit < 0) {
            throw new IllegalStateException("Payment plan token limit must be non-negative for " + code.name());
        }
        if (monthlyPriceCents < 0) {
            throw new IllegalStateException("Payment plan price must be non-negative for " + code.name());
        }
        return new PaymentPlanDefinition(
                code,
                displayName.trim(),
                defaultString(description),
                monthlyTokenLimit,
                monthlyPriceCents,
                defaultCurrency(currency)
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
