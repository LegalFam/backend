package com.legalfam.backend.auth.domain.exception;

import com.legalfam.backend.common.error.ApiErrorDescriptor;

public class EmailNotVerifiedException extends RuntimeException {
    private final AuthApiError error;

    private EmailNotVerifiedException() {
        super(AuthApiError.EMAIL_NOT_VERIFIED.message());
        this.error = AuthApiError.EMAIL_NOT_VERIFIED;
    }

    public static EmailNotVerifiedException forLogin() {
        return new EmailNotVerifiedException();
    }

    public ApiErrorDescriptor error() {
        return error;
    }
}
