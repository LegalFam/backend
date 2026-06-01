package com.legalfam.backend.payment.application.service;

import com.legalfam.backend.auth.application.port.out.UserPort;
import com.legalfam.backend.auth.domain.model.User;
import com.legalfam.backend.payment.application.dto.CreateCheckoutSessionRequest;
import com.legalfam.backend.payment.application.dto.CreateCheckoutSessionResponse;
import com.legalfam.backend.payment.application.dto.PaymentPlanDefinition;
import com.legalfam.backend.payment.application.dto.PaymentPlanResponse;
import com.legalfam.backend.payment.application.dto.PaymentSubscriptionResponse;
import com.legalfam.backend.payment.application.dto.PaymentWebhookNotification;
import com.legalfam.backend.payment.application.port.in.PaymentProvisioningUseCase;
import com.legalfam.backend.payment.application.port.in.PaymentTokenUseCase;
import com.legalfam.backend.payment.application.port.in.PaymentUseCase;
import com.legalfam.backend.payment.application.port.out.PaymentGatewayPort;
import com.legalfam.backend.payment.application.port.out.PaymentPlanCatalogPort;
import com.legalfam.backend.payment.application.port.out.PaymentPersistencePort;
import com.legalfam.backend.payment.domain.exception.InsufficientTokensException;
import com.legalfam.backend.payment.domain.exception.InvalidPaymentRequestException;
import com.legalfam.backend.payment.domain.exception.PaymentWebhookException;
import com.legalfam.backend.payment.domain.exception.SubscriptionInactiveException;
import com.legalfam.backend.payment.domain.exception.SubscriptionNotFoundException;
import com.legalfam.backend.payment.domain.model.PaymentProvider;
import com.legalfam.backend.payment.domain.model.Subscription;
import com.legalfam.backend.payment.domain.model.SubscriptionPlanCode;
import com.legalfam.backend.payment.domain.model.SubscriptionStatus;
import com.legalfam.backend.payment.domain.model.TokenTransaction;
import com.legalfam.backend.payment.domain.model.TokenTransactionType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService implements PaymentUseCase, PaymentProvisioningUseCase, PaymentTokenUseCase {

    private static final String BILLING_INTERVAL = "month";

    private final PaymentPersistencePort paymentPersistencePort;
    private final PaymentGatewayPort paymentGatewayPort;
    private final PaymentPlanCatalogPort paymentPlanCatalogPort;
    private final UserPort userPort;
    private final String defaultCheckoutSuccessUrl;
    private final String defaultCheckoutCancelUrl;
    private final Clock clock = Clock.systemUTC();

    public PaymentService(
            PaymentPersistencePort paymentPersistencePort,
            PaymentGatewayPort paymentGatewayPort,
            PaymentPlanCatalogPort paymentPlanCatalogPort,
            UserPort userPort,
            @Value("${app.payment.mercado-pago.checkout-success-url}") String defaultCheckoutSuccessUrl,
            @Value("${app.payment.mercado-pago.checkout-cancel-url:http://localhost:3000/billing/cancel}") String defaultCheckoutCancelUrl
    ) {
        this.paymentPersistencePort = paymentPersistencePort;
        this.paymentGatewayPort = paymentGatewayPort;
        this.paymentPlanCatalogPort = paymentPlanCatalogPort;
        this.userPort = userPort;
        this.defaultCheckoutSuccessUrl = defaultCheckoutSuccessUrl;
        this.defaultCheckoutCancelUrl = defaultCheckoutCancelUrl;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentPlanResponse> listPlans(UUID userId) {
        Subscription subscription = userId == null ? null : paymentPersistencePort.findSubscriptionByUserId(userId).orElse(null);
        SubscriptionPlanCode currentPlanCode = userId == null
                ? null
                : subscription == null ? SubscriptionPlanCode.FREE : subscription.getPlanCode();
        return paymentPlanCatalogPort.listPlans().stream()
                .map(plan -> new PaymentPlanResponse(
                        plan.code().name(),
                        plan.displayName(),
                        plan.description(),
                        BILLING_INTERVAL,
                        plan.isFree() ? null : plan.monthlyPriceCents(),
                        plan.currency(),
                        plan.monthlyTokenLimit(),
                        plan.code() == currentPlanCode,
                        plan.isFree() || plan.isPurchasable()
                ))
                .toList();
    }

    @Override
    @Transactional
    public PaymentSubscriptionResponse getSubscription(UUID userId) {
        Subscription subscription = getOrCreateSubscription(userId);
        refreshFreeSubscriptionIfNeeded(subscription);
        return toSubscriptionResponse(subscription);
    }

    @Override
    @Transactional
    public CreateCheckoutSessionResponse createCheckoutSession(UUID userId, CreateCheckoutSessionRequest request) {
        if (request == null) {
            throw new InvalidPaymentRequestException("Checkout request is required");
        }
        PaymentPlanDefinition plan = paymentPlanCatalogPort.getPaidPlanOrThrow(request.planCode());
        User user = getRequiredUser(userId);
        Subscription subscription = getOrCreateSubscription(userId);
        refreshFreeSubscriptionIfNeeded(subscription);
        ensureCheckoutAllowed(subscription, plan);

        String checkoutUrl = paymentGatewayPort.createCheckoutSession(
                userId,
                user.getEmail(),
                plan,
                firstNonBlank(request.successUrl(), defaultCheckoutSuccessUrl),
                firstNonBlank(request.cancelUrl(), defaultCheckoutCancelUrl)
        );
        return new CreateCheckoutSessionResponse(checkoutUrl);
    }

    @Override
    @Transactional
    public void cancelSubscription(UUID userId) {
        Subscription subscription = getOrCreateSubscription(userId);
        refreshFreeSubscriptionIfNeeded(subscription);

        if (subscription.getProvider() != PaymentProvider.MERCADO_PAGO
                || subscription.getGatewaySubscriptionId() == null
                || subscription.getGatewaySubscriptionId().isBlank()) {
            throw new InvalidPaymentRequestException("No Mercado Pago subscription is available to cancel");
        }

        paymentGatewayPort.cancelSubscription(subscription.getGatewaySubscriptionId());
        downgradeToFreePlan(subscription, "Allocated tokens after Mercado Pago subscription cancellation");
    }

    @Override
    @Transactional
    public void handleWebhook(String payload, String signatureHeader) {
        if (payload == null || payload.isBlank()) {
            throw new InvalidPaymentRequestException("Webhook payload is required");
        }

        PaymentWebhookNotification notification = paymentGatewayPort.parseWebhook(payload, signatureHeader);
        if (notification.eventId() != null
                && !notification.eventId().isBlank()
                && paymentPersistencePort.existsProcessedWebhookEvent(notification.eventId())) {
            return;
        }

        if (notification.subscriptionId() != null && !notification.subscriptionId().isBlank()) {
            if (isCanceledStatus(notification.status())) {
                handleSubscriptionDeleted(notification);
            } else {
                syncSubscription(notification, notification.resetPeriod());
            }
        }

        if (notification.eventId() != null && !notification.eventId().isBlank()) {
            paymentPersistencePort.saveProcessedWebhookEvent(notification.eventId(), notification.eventType(), now());
        }
    }

    @Override
    @Transactional
    public void provisionFreeSubscription(UUID userId) {
        if (paymentPersistencePort.findSubscriptionByUserId(userId).isPresent()) {
            return;
        }
        User user = getRequiredUser(userId);
        createFreeSubscription(user.getId(), now());
    }

    @Override
    @Transactional
    public void consumeChatToken(UUID userId, UUID chatMessageId) {
        if (chatMessageId == null) {
            throw new InvalidPaymentRequestException("Chat message id is required");
        }
        if (paymentPersistencePort.existsTokenTransactionByChatMessageIdAndType(
                chatMessageId,
                TokenTransactionType.CHAT_CONSUMPTION
        )) {
            return;
        }

        Subscription subscription = getOrCreateSubscription(userId);
        refreshFreeSubscriptionIfNeeded(subscription);
        ensureSubscriptionActive(subscription);
        if (subscription.getRemainingTokens() <= 0) {
            throw new InsufficientTokensException("No chat tokens remaining for the current period");
        }

        subscription.setRemainingTokens(subscription.getRemainingTokens() - 1);
        subscription.setUpdatedAt(now());
        subscription = paymentPersistencePort.saveSubscription(subscription);
        saveTokenTransaction(
                subscription,
                chatMessageId,
                TokenTransactionType.CHAT_CONSUMPTION,
                -1,
                "Chat token consumed for user message"
        );
    }

    @Override
    @Transactional
    public void refundChatToken(UUID chatMessageId) {
        if (chatMessageId == null) {
            return;
        }
        if (paymentPersistencePort.existsTokenTransactionByChatMessageIdAndType(chatMessageId, TokenTransactionType.CHAT_REFUND)) {
            return;
        }

        TokenTransaction consumption = paymentPersistencePort.findTokenTransactionByChatMessageIdAndType(
                chatMessageId,
                TokenTransactionType.CHAT_CONSUMPTION
        ).orElse(null);
        if (consumption == null) {
            return;
        }

        Subscription subscription = paymentPersistencePort.findSubscriptionById(consumption.getSubscriptionId()).orElse(null);
        if (subscription == null) {
            return;
        }

        int nextRemainingTokens = Math.min(subscription.getMonthlyTokenLimit(), subscription.getRemainingTokens() + 1);
        int delta = nextRemainingTokens - subscription.getRemainingTokens();
        subscription.setRemainingTokens(nextRemainingTokens);
        subscription.setUpdatedAt(now());
        subscription = paymentPersistencePort.saveSubscription(subscription);
        saveTokenTransaction(
                subscription,
                chatMessageId,
                TokenTransactionType.CHAT_REFUND,
                delta,
                "Refunded chat token after assistant processing failure"
        );
    }

    private PaymentSubscriptionResponse toSubscriptionResponse(Subscription subscription) {
        return new PaymentSubscriptionResponse(
                subscription.getPlanCode().name(),
                subscription.getStatus().name(),
                subscription.getProvider().name(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.isCancelAtPeriodEnd(),
                subscription.getMonthlyTokenLimit(),
                subscription.getRemainingTokens()
        );
    }

    private Subscription getOrCreateSubscription(UUID userId) {
        return paymentPersistencePort.findSubscriptionByUserId(userId)
                .map(existing -> {
                    refreshFreeSubscriptionIfNeeded(existing);
                    return existing;
                })
                .orElseGet(() -> {
                    User user = getRequiredUser(userId);
                    return createFreeSubscription(user.getId(), now());
                });
    }

    private Subscription createFreeSubscription(UUID userId, Instant anchor) {
        PaymentPlanDefinition freePlan = paymentPlanCatalogPort.getFreePlan();
        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setPlanCode(freePlan.code());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setProvider(PaymentProvider.FREE);
        subscription.setGatewayCustomerId(null);
        subscription.setGatewaySubscriptionId(null);
        subscription.setCurrentPeriodStart(anchor);
        subscription.setCurrentPeriodEnd(addMonths(anchor, 1));
        subscription.setCancelAtPeriodEnd(false);
        subscription.setMonthlyTokenLimit(freePlan.monthlyTokenLimit());
        subscription.setRemainingTokens(freePlan.monthlyTokenLimit());
        subscription.setCreatedAt(anchor);
        subscription.setUpdatedAt(anchor);
        subscription = paymentPersistencePort.saveSubscription(subscription);
        saveTokenTransaction(subscription, null, TokenTransactionType.PERIOD_ALLOCATION,
                freePlan.monthlyTokenLimit(), "Allocated monthly tokens for free plan");
        return subscription;
    }

    private void refreshFreeSubscriptionIfNeeded(Subscription subscription) {
        if (subscription.getProvider() != PaymentProvider.FREE) {
            return;
        }
        Instant now = now();
        if (subscription.getCurrentPeriodEnd() != null && now.isBefore(subscription.getCurrentPeriodEnd())) {
            return;
        }

        Instant periodStart = subscription.getCurrentPeriodStart();
        Instant periodEnd = subscription.getCurrentPeriodEnd();
        if (periodStart == null || periodEnd == null) {
            periodStart = now;
            periodEnd = addMonths(now, 1);
        }
        while (!now.isBefore(periodEnd)) {
            periodStart = periodEnd;
            periodEnd = addMonths(periodEnd, 1);
        }

        int delta = subscription.getMonthlyTokenLimit() - subscription.getRemainingTokens();
        subscription.setCurrentPeriodStart(periodStart);
        subscription.setCurrentPeriodEnd(periodEnd);
        subscription.setRemainingTokens(subscription.getMonthlyTokenLimit());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setUpdatedAt(now);
        Subscription saved = paymentPersistencePort.saveSubscription(subscription);
        saveTokenTransaction(saved, null, TokenTransactionType.PERIOD_ALLOCATION, delta,
                "Allocated monthly tokens for new free-plan period");
    }

    private void ensureSubscriptionActive(Subscription subscription) {
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new SubscriptionInactiveException("Subscription is not active");
        }
    }

    private void ensureCheckoutAllowed(Subscription subscription, PaymentPlanDefinition targetPlan) {
        if (subscription.getPlanCode() == targetPlan.code()
                && subscription.getProvider() == PaymentProvider.MERCADO_PAGO
                && subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            throw new InvalidPaymentRequestException("User is already subscribed to the selected plan");
        }
        boolean managedInGateway = subscription.getProvider() == PaymentProvider.MERCADO_PAGO
                && subscription.getGatewaySubscriptionId() != null
                && !subscription.getGatewaySubscriptionId().isBlank()
                && subscription.getStatus() != SubscriptionStatus.CANCELED
                && subscription.getStatus() != SubscriptionStatus.EXPIRED;
        if (managedInGateway) {
            throw new InvalidPaymentRequestException("Cancel the current Mercado Pago subscription before changing plans");
        }
    }

    private void syncSubscription(PaymentWebhookNotification notification, boolean forcePeriodAllocation) {
        if (notification.planCode() == null || notification.planCode().isBlank()) {
            return;
        }

        PaymentPlanDefinition plan = paymentPlanCatalogPort.getPaidPlanOrThrow(notification.planCode());
        Subscription subscription = resolveGatewayBackedSubscription(notification);
        SubscriptionPlanCode previousPlan = subscription.getPlanCode();
        Instant previousPeriodStart = subscription.getCurrentPeriodStart();
        int previousRemainingTokens = subscription.getRemainingTokens();
        int previousLimit = subscription.getMonthlyTokenLimit();

        Instant nextPeriodStart = notification.currentPeriodStart() != null
                ? notification.currentPeriodStart()
                : subscription.getCurrentPeriodStart();
        if (nextPeriodStart == null) {
            nextPeriodStart = now();
        }
        Instant nextPeriodEnd = notification.currentPeriodEnd() != null
                ? notification.currentPeriodEnd()
                : subscription.getCurrentPeriodEnd();
        if (nextPeriodEnd == null || !nextPeriodEnd.isAfter(nextPeriodStart)) {
            nextPeriodEnd = addMonths(nextPeriodStart, 1);
        }

        subscription.setPlanCode(plan.code());
        subscription.setProvider(PaymentProvider.MERCADO_PAGO);
        subscription.setStatus(mapGatewayStatus(notification.status()));
        subscription.setGatewayCustomerId(notification.customerId());
        subscription.setGatewaySubscriptionId(notification.subscriptionId());
        subscription.setCurrentPeriodStart(nextPeriodStart);
        subscription.setCurrentPeriodEnd(nextPeriodEnd);
        subscription.setCancelAtPeriodEnd(notification.cancelAtPeriodEnd());
        subscription.setMonthlyTokenLimit(plan.monthlyTokenLimit());
        if (subscription.getCreatedAt() == null) {
            subscription.setCreatedAt(now());
        }

        boolean periodChanged = previousPeriodStart == null || !previousPeriodStart.equals(nextPeriodStart);
        if (forcePeriodAllocation || periodChanged) {
            subscription.setRemainingTokens(plan.monthlyTokenLimit());
        } else if (previousPlan != plan.code()) {
            int usedTokens = Math.max(previousLimit - previousRemainingTokens, 0);
            subscription.setRemainingTokens(Math.max(plan.monthlyTokenLimit() - usedTokens, 0));
        }
        subscription.setUpdatedAt(now());
        Subscription saved = paymentPersistencePort.saveSubscription(subscription);

        if (forcePeriodAllocation || periodChanged) {
            saveTokenTransaction(
                    saved,
                    null,
                    TokenTransactionType.PERIOD_ALLOCATION,
                    saved.getRemainingTokens() - previousRemainingTokens,
                    "Allocated tokens for Mercado Pago billing period"
            );
        } else if (previousPlan != plan.code()) {
            saveTokenTransaction(
                    saved,
                    null,
                    TokenTransactionType.PLAN_CHANGE_ADJUSTMENT,
                    saved.getRemainingTokens() - previousRemainingTokens,
                    "Adjusted remaining tokens after plan change"
            );
        }
    }

    private void handleSubscriptionDeleted(PaymentWebhookNotification notification) {
        Subscription subscription = resolveGatewayBackedSubscription(notification);
        downgradeToFreePlan(subscription, "Allocated tokens after Mercado Pago subscription cancellation");
    }

    private void downgradeToFreePlan(Subscription subscription, String description) {
        PaymentPlanDefinition freePlan = paymentPlanCatalogPort.getFreePlan();
        int previousRemainingTokens = subscription.getRemainingTokens();
        Instant anchor = now();

        subscription.setPlanCode(freePlan.code());
        subscription.setProvider(PaymentProvider.FREE);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setGatewayCustomerId(null);
        subscription.setGatewaySubscriptionId(null);
        subscription.setCurrentPeriodStart(anchor);
        subscription.setCurrentPeriodEnd(addMonths(anchor, 1));
        subscription.setCancelAtPeriodEnd(false);
        subscription.setMonthlyTokenLimit(freePlan.monthlyTokenLimit());
        subscription.setRemainingTokens(freePlan.monthlyTokenLimit());
        subscription.setUpdatedAt(anchor);
        Subscription saved = paymentPersistencePort.saveSubscription(subscription);
        saveTokenTransaction(
                saved,
                null,
                TokenTransactionType.PERIOD_ALLOCATION,
                saved.getRemainingTokens() - previousRemainingTokens,
                description
        );
    }

    private Subscription resolveGatewayBackedSubscription(PaymentWebhookNotification notification) {
        Subscription subscription = null;
        if (notification.subscriptionId() != null && !notification.subscriptionId().isBlank()) {
            subscription = paymentPersistencePort.findSubscriptionByGatewaySubscriptionId(notification.subscriptionId())
                    .orElse(null);
        }
        if (subscription == null && notification.customerId() != null && !notification.customerId().isBlank()) {
            subscription = paymentPersistencePort.findSubscriptionByGatewayCustomerId(notification.customerId())
                    .orElse(null);
        }
        if (subscription == null && notification.userId() != null) {
            subscription = paymentPersistencePort.findSubscriptionByUserId(notification.userId()).orElse(null);
        }
        if (subscription == null) {
            if (notification.userId() == null) {
                throw new PaymentWebhookException("Payment webhook cannot be matched to a local user");
            }
            subscription = createFreeSubscription(notification.userId(), now());
        }
        return subscription;
    }

    private SubscriptionStatus mapGatewayStatus(String gatewayStatus) {
        if (gatewayStatus == null || gatewayStatus.isBlank()) {
            return SubscriptionStatus.INCOMPLETE;
        }
        return switch (gatewayStatus.trim().toLowerCase(Locale.ROOT)) {
            case "authorized", "active" -> SubscriptionStatus.ACTIVE;
            case "paused" -> SubscriptionStatus.PAST_DUE;
            case "cancelled", "canceled" -> SubscriptionStatus.CANCELED;
            default -> SubscriptionStatus.INCOMPLETE;
        };
    }

    private boolean isCanceledStatus(String gatewayStatus) {
        if (gatewayStatus == null || gatewayStatus.isBlank()) {
            return false;
        }
        String normalized = gatewayStatus.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("cancelled") || normalized.equals("canceled");
    }

    private void saveTokenTransaction(
            Subscription subscription,
            UUID chatMessageId,
            TokenTransactionType type,
            int tokenDelta,
            String description
    ) {
        TokenTransaction transaction = new TokenTransaction();
        transaction.setSubscriptionId(subscription.getId());
        transaction.setUserId(subscription.getUserId());
        transaction.setChatMessageId(chatMessageId);
        transaction.setType(type);
        transaction.setTokenDelta(tokenDelta);
        transaction.setDescription(description);
        transaction.setCreatedAt(now());
        paymentPersistencePort.saveTokenTransaction(transaction);
    }

    private User getRequiredUser(UUID userId) {
        return userPort.findById(userId)
                .orElseThrow(() -> new SubscriptionNotFoundException("Authenticated user not found"));
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private Instant addMonths(Instant instant, int months) {
        return ZonedDateTime.ofInstant(instant, ZoneOffset.UTC).plusMonths(months).toInstant();
    }

    private String firstNonBlank(String candidate, String fallback) {
        if (candidate != null && !candidate.isBlank()) {
            return candidate.trim();
        }
        return fallback;
    }
}
