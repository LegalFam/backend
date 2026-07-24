package com.legalfam.backend.payment.application.service;

import com.legalfam.backend.common.identity.UserIdentity;
import com.legalfam.backend.common.identity.application.port.out.IUserIdentityPort;
import com.legalfam.backend.payment.application.dto.CreateCheckoutSessionRequest;
import com.legalfam.backend.payment.application.dto.CreateCheckoutSessionResponse;
import com.legalfam.backend.payment.application.dto.PaymentPlanDefinition;
import com.legalfam.backend.payment.application.dto.PaymentPlanResponse;
import com.legalfam.backend.payment.application.dto.PaymentSubscriptionResponse;
import com.legalfam.backend.payment.application.dto.PaymentWebhookNotification;
import com.legalfam.backend.payment.application.port.in.IPaymentProvisioningUseCase;
import com.legalfam.backend.payment.application.port.in.IPaymentTokenUseCase;
import com.legalfam.backend.payment.application.port.in.IPaymentUseCase;
import com.legalfam.backend.payment.application.port.out.IPaymentGatewayPort;
import com.legalfam.backend.payment.application.port.out.IPaymentPlanCatalogPort;
import com.legalfam.backend.payment.application.port.out.IPaymentPersistencePort;
import com.legalfam.backend.payment.domain.exception.InvalidPaymentRequestException;
import com.legalfam.backend.payment.domain.exception.PaymentApiError;
import com.legalfam.backend.payment.domain.exception.PaymentWebhookException;
import com.legalfam.backend.payment.domain.exception.SubscriptionNotFoundException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService implements IPaymentUseCase, IPaymentProvisioningUseCase, IPaymentTokenUseCase {

    private static final String BILLING_INTERVAL = "month";

    private final IPaymentPersistencePort IPaymentPersistencePort;
    private final IPaymentGatewayPort IPaymentGatewayPort;
    private final IPaymentPlanCatalogPort IPaymentPlanCatalogPort;
    private final IUserIdentityPort IUserIdentityPort;
    private final String defaultCheckoutSuccessUrl;
    private final String defaultCheckoutCancelUrl;
    private final Clock clock;

    public PaymentService(
            IPaymentPersistencePort IPaymentPersistencePort,
            IPaymentGatewayPort IPaymentGatewayPort,
            IPaymentPlanCatalogPort IPaymentPlanCatalogPort,
            IUserIdentityPort IUserIdentityPort,
            PaymentCheckoutProperties paymentCheckoutProperties,
            Clock clock
    ) {
        this.IPaymentPersistencePort = IPaymentPersistencePort;
        this.IPaymentGatewayPort = IPaymentGatewayPort;
        this.IPaymentPlanCatalogPort = IPaymentPlanCatalogPort;
        this.IUserIdentityPort = IUserIdentityPort;
        this.defaultCheckoutSuccessUrl = paymentCheckoutProperties.defaultCheckoutSuccessUrl();
        this.defaultCheckoutCancelUrl = paymentCheckoutProperties.defaultCheckoutCancelUrl();
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentPlanResponse> listPlans(UUID userId) {
        Subscription subscription = userId == null ? null : IPaymentPersistencePort.findSubscriptionByUserId(userId).orElse(null);
        SubscriptionPlanCode currentPlanCode = userId == null
                ? null
                : subscription == null ? SubscriptionPlanCode.FREE : subscription.getPlanCode();
        return IPaymentPlanCatalogPort.listPlans().stream()
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
        settlePeriodIfNeeded(subscription);
        return toSubscriptionResponse(subscription);
    }

    @Override
    @Transactional
    public CreateCheckoutSessionResponse createCheckoutSession(UUID userId, CreateCheckoutSessionRequest request) {
        if (request == null) {
            throw InvalidPaymentRequestException.of(PaymentApiError.CHECKOUT_REQUEST_REQUIRED);
        }
        PaymentPlanDefinition plan = IPaymentPlanCatalogPort.getPaidPlanOrThrow(request.planCode());
        UserIdentity user = getRequiredUser(userId);
        Subscription subscription = getOrCreateSubscription(userId);
        settlePeriodIfNeeded(subscription);
        ensureCheckoutAllowed(subscription, plan);

        String checkoutUrl = IPaymentGatewayPort.createCheckoutSession(
                userId,
                user.email(),
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
        settlePeriodIfNeeded(subscription);

        if (!subscription.hasGatewaySubscription()) {
            throw InvalidPaymentRequestException.of(PaymentApiError.NO_GATEWAY_SUBSCRIPTION_TO_CANCEL);
        }
        if (subscription.isCancellationScheduled()) {
            throw InvalidPaymentRequestException.of(PaymentApiError.SUBSCRIPTION_ALREADY_CANCELED);
        }

        IPaymentGatewayPort.cancelSubscription(subscription.getGatewaySubscriptionId());

        // The gateway stops renewing, but the period the user already paid for is honored:
        // plan, token balance and period end stay as they are until settlePeriodIfNeeded downgrades them.
        subscription.scheduleCancellationAtPeriodEnd(now());
        IPaymentPersistencePort.saveSubscription(subscription);
    }

    @Override
    @Transactional
    public void handleWebhook(String payload, String signatureHeader, String requestId, String dataId) {
        if (payload == null || payload.isBlank()) {
            throw InvalidPaymentRequestException.of(PaymentApiError.WEBHOOK_PAYLOAD_REQUIRED);
        }

        PaymentWebhookNotification notification = IPaymentGatewayPort.parseVerifiedWebhook(
                payload,
                signatureHeader,
                requestId,
                dataId
        );
        if (notification.eventId() != null
                && !notification.eventId().isBlank()
                && !IPaymentPersistencePort.tryRecordProcessedWebhookEvent(
                        notification.eventId(),
                        notification.eventType(),
                        now()
                )) {
            return;
        }

        if (notification.subscriptionId() != null && !notification.subscriptionId().isBlank()) {
            if (isCanceledStatus(notification.status())) {
                handleSubscriptionDeleted(notification);
            } else {
                syncSubscription(notification, notification.resetPeriod());
            }
        }

    }

    @Override
    @Transactional
    public void provisionFreeSubscription(UUID userId) {
        if (IPaymentPersistencePort.findSubscriptionByUserId(userId).isPresent()) {
            return;
        }
        UserIdentity user = getRequiredUser(userId);
        createFreeSubscription(user.id(), now());
    }

    @Override
    @Transactional
    public void consumeChatTokensForAssistantResult(UUID userId, UUID chatMessageId, int tokenCost) {
        if (chatMessageId == null || tokenCost <= 0) {
            return;
        }
        int requestedTotalCost = Math.clamp(tokenCost, 1, 3);

        if (IPaymentPersistencePort.existsTokenTransactionByChatMessageIdAndType(
                chatMessageId,
                TokenTransactionType.CHAT_CONSUMPTION
        )) {
            return;
        }
        consumeResolvedChatTokens(userId, chatMessageId, requestedTotalCost);
    }

    private void consumeResolvedChatTokens(UUID userId, UUID chatMessageId, int requestedTotalCost) {
        Subscription subscription = getOrCreateSubscriptionForUpdate(userId);
        if (IPaymentPersistencePort.existsTokenTransactionByChatMessageIdAndType(
                chatMessageId,
                TokenTransactionType.CHAT_CONSUMPTION
        )) {
            return;
        }
        settlePeriodIfNeeded(subscription);
        int consumedTokens = 0;
        if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            consumedTokens = subscription.consumeAvailableChatTokens(requestedTotalCost, now());
            if (consumedTokens > 0) {
                subscription = IPaymentPersistencePort.saveSubscription(subscription);
            }
        }
        saveTokenTransaction(
                subscription,
                chatMessageId,
                TokenTransactionType.CHAT_CONSUMPTION,
                -consumedTokens,
                "Chat tokens consumed after assistant route resolution"
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
        return IPaymentPersistencePort.findSubscriptionByUserId(userId)
                .map(existing -> {
                    settlePeriodIfNeeded(existing);
                    return existing;
                })
                .orElseGet(() -> {
                    UserIdentity user = getRequiredUser(userId);
                    return createFreeSubscription(user.id(), now());
                });
    }

    private Subscription getOrCreateSubscriptionForUpdate(UUID userId) {
        return IPaymentPersistencePort.findSubscriptionByUserIdForUpdate(userId)
                .map(existing -> {
                    settlePeriodIfNeeded(existing);
                    return existing;
                })
                .orElseGet(() -> {
                    UserIdentity user = getRequiredUser(userId);
                    return createFreeSubscription(user.id(), now());
                });
    }

    private Subscription createFreeSubscription(UUID userId, Instant anchor) {
        PaymentPlanDefinition freePlan = IPaymentPlanCatalogPort.getFreePlan();
        Subscription subscription = Subscription.createFree(
                userId,
                freePlan.code(),
                freePlan.monthlyTokenLimit(),
                anchor,
                addMonths(anchor, 1),
                anchor
        );
        subscription = IPaymentPersistencePort.saveSubscription(subscription);
        saveTokenTransaction(subscription, null, TokenTransactionType.PERIOD_ALLOCATION,
                freePlan.monthlyTokenLimit(), "Allocated monthly tokens for free plan");
        return subscription;
    }

    /**
     * Applies whatever the calendar owes the subscription before it is read or charged:
     * a scheduled cancellation whose paid period already ended, or a new free-plan period.
     * There is no scheduler in this service, so every entry point settles lazily.
     */
    private void settlePeriodIfNeeded(Subscription subscription) {
        if (subscription.isCancellationDueAt(now())) {
            downgradeToFreePlan(subscription, "Allocated tokens after scheduled subscription cancellation");
            return;
        }
        refreshFreeSubscriptionIfNeeded(subscription);
    }

    private void refreshFreeSubscriptionIfNeeded(Subscription subscription) {
        Instant now = now();
        if (!subscription.shouldRefreshFreePeriodAt(now)) {
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

        int delta = subscription.allocateCurrentPeriodTokens(periodStart, periodEnd, now);
        Subscription saved = IPaymentPersistencePort.saveSubscription(subscription);
        saveTokenTransaction(saved, null, TokenTransactionType.PERIOD_ALLOCATION, delta,
                "Allocated monthly tokens for new free-plan period");
    }

    private void ensureCheckoutAllowed(Subscription subscription, PaymentPlanDefinition targetPlan) {
        subscription.assertCheckoutAllowedFor(targetPlan.code());
    }

    private void syncSubscription(PaymentWebhookNotification notification, boolean forcePeriodAllocation) {
        if (notification.planCode() == null || notification.planCode().isBlank()) {
            return;
        }

        PaymentPlanDefinition plan = IPaymentPlanCatalogPort.getPaidPlanOrThrow(notification.planCode());
        Subscription subscription = resolveGatewayBackedSubscription(notification);
        SubscriptionPlanCode previousPlan = subscription.getPlanCode();
        Instant previousPeriodStart = subscription.getCurrentPeriodStart();
        int previousRemainingTokens = subscription.getRemainingTokens();

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

        boolean periodChanged = previousPeriodStart == null || !previousPeriodStart.equals(nextPeriodStart);
        subscription.syncGatewaySubscription(
                plan.code(),
                mapGatewayStatus(notification.status()),
                notification.customerId(),
                notification.subscriptionId(),
                nextPeriodStart,
                nextPeriodEnd,
                notification.cancelAtPeriodEnd(),
                plan.monthlyTokenLimit(),
                forcePeriodAllocation || periodChanged,
                now()
        );
        Subscription saved = IPaymentPersistencePort.saveSubscription(subscription);

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

        // Mercado Pago fires this right after our own cancelSubscription call. Downgrading here
        // would wipe the balance the user already paid for, so only schedule the downgrade and
        // let settlePeriodIfNeeded apply it once the period is actually over.
        if (subscription.hasPaidPeriodRemainingAt(now())) {
            subscription.scheduleCancellationAtPeriodEnd(now());
            IPaymentPersistencePort.saveSubscription(subscription);
            return;
        }

        downgradeToFreePlan(subscription, "Allocated tokens after Mercado Pago subscription cancellation");
    }

    private void downgradeToFreePlan(Subscription subscription, String description) {
        PaymentPlanDefinition freePlan = IPaymentPlanCatalogPort.getFreePlan();
        int previousRemainingTokens = subscription.getRemainingTokens();
        Instant anchor = now();

        subscription.activateFreePlan(freePlan.code(), freePlan.monthlyTokenLimit(), anchor, addMonths(anchor, 1), anchor);
        Subscription saved = IPaymentPersistencePort.saveSubscription(subscription);
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
            subscription = IPaymentPersistencePort.findSubscriptionByGatewaySubscriptionId(notification.subscriptionId())
                    .orElse(null);
        }
        if (subscription == null && notification.customerId() != null && !notification.customerId().isBlank()) {
            subscription = IPaymentPersistencePort.findSubscriptionByGatewayCustomerId(notification.customerId())
                    .orElse(null);
        }
        if (subscription == null && notification.userId() != null) {
            subscription = IPaymentPersistencePort.findSubscriptionByUserId(notification.userId()).orElse(null);
        }
        if (subscription == null) {
            if (notification.userId() == null) {
                throw PaymentWebhookException.of(PaymentApiError.PAYMENT_WEBHOOK_UNMATCHED_USER);
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
        TokenTransaction transaction = TokenTransaction.create(
                subscription.getId(),
                subscription.getUserId(),
                chatMessageId,
                type,
                tokenDelta,
                description,
                now()
        );
        IPaymentPersistencePort.saveTokenTransaction(transaction);
    }

    private UserIdentity getRequiredUser(UUID userId) {
        return IUserIdentityPort.findUserIdentityById(userId)
                .orElseThrow(SubscriptionNotFoundException::notFound);
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
