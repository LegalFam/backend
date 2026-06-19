package com.legalfam.backend.payment.domain.exception;

import com.legalfam.backend.common.error.ApiErrorDescriptor;

public class PaymentGatewayException extends RuntimeException {
    private final PaymentApiError error;

    private PaymentGatewayException(PaymentApiError error) {
        super(error.message());
        this.error = error;
    }

    private PaymentGatewayException(PaymentApiError error, Throwable cause) {
        super(error.message(), cause);
        this.error = error;
    }

    public static PaymentGatewayException of(PaymentApiError error) {
        return new PaymentGatewayException(error);
    }

    public static PaymentGatewayException of(PaymentApiError error, Throwable cause) {
        return new PaymentGatewayException(error, cause);
    }

    public ApiErrorDescriptor error() {
        return error;
    }
}
