package com.legalfam.backend.payment.domain.exception;

import com.legalfam.backend.common.error.ApiErrorDescriptor;

public class PaymentWebhookException extends RuntimeException {
    private final PaymentApiError error;

    private PaymentWebhookException(PaymentApiError error) {
        super(error.message());
        this.error = error;
    }

    private PaymentWebhookException(PaymentApiError error, Throwable cause) {
        super(error.message(), cause);
        this.error = error;
    }

    public static PaymentWebhookException of(PaymentApiError error) {
        return new PaymentWebhookException(error);
    }

    public static PaymentWebhookException of(PaymentApiError error, Throwable cause) {
        return new PaymentWebhookException(error, cause);
    }

    public ApiErrorDescriptor error() {
        return error;
    }
}
