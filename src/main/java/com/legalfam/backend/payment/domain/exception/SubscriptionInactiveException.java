package com.legalfam.backend.payment.domain.exception;

import com.legalfam.backend.common.error.ApiErrorDescriptor;

public class SubscriptionInactiveException extends RuntimeException {
    private final PaymentApiError error;

    private SubscriptionInactiveException() {
        super(PaymentApiError.SUBSCRIPTION_INACTIVE.message());
        this.error = PaymentApiError.SUBSCRIPTION_INACTIVE;
    }

    public static SubscriptionInactiveException inactive() {
        return new SubscriptionInactiveException();
    }

    public ApiErrorDescriptor error() {
        return error;
    }
}
