package com.legalfam.backend.payment.domain.exception;

import com.legalfam.backend.common.error.ApiErrorDescriptor;

public class InvalidPaymentRequestException extends RuntimeException {
    private final PaymentApiError error;

    private InvalidPaymentRequestException(PaymentApiError error) {
        super(error.message());
        this.error = error;
    }

    public static InvalidPaymentRequestException of(PaymentApiError error) {
        return new InvalidPaymentRequestException(error);
    }

    public ApiErrorDescriptor error() {
        return error;
    }
}
