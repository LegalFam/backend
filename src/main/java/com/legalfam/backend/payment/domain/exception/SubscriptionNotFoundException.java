package com.legalfam.backend.payment.domain.exception;

import com.legalfam.backend.common.error.ApiErrorDescriptor;

public class SubscriptionNotFoundException extends RuntimeException {
    private final PaymentApiError error;

    private SubscriptionNotFoundException() {
        super(PaymentApiError.SUBSCRIPTION_NOT_FOUND.message());
        this.error = PaymentApiError.SUBSCRIPTION_NOT_FOUND;
    }

    public static SubscriptionNotFoundException notFound() {
        return new SubscriptionNotFoundException();
    }

    public ApiErrorDescriptor error() {
        return error;
    }
}
