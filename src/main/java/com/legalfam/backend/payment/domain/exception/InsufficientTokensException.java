package com.legalfam.backend.payment.domain.exception;

import com.legalfam.backend.common.error.ApiErrorDescriptor;

public class InsufficientTokensException extends RuntimeException {
    private final PaymentApiError error;

    private InsufficientTokensException() {
        super(PaymentApiError.INSUFFICIENT_TOKENS.message());
        this.error = PaymentApiError.INSUFFICIENT_TOKENS;
    }

    public static InsufficientTokensException insufficientTokens() {
        return new InsufficientTokensException();
    }

    public ApiErrorDescriptor error() {
        return error;
    }
}
