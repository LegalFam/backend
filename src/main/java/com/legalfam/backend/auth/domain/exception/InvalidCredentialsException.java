package com.legalfam.backend.auth.domain.exception;

import com.legalfam.backend.common.error.ApiErrorDescriptor;

public class InvalidCredentialsException extends RuntimeException {
    private final AuthApiError error;

    private InvalidCredentialsException() {
        super(AuthApiError.INVALID_CREDENTIALS.message());
        this.error = AuthApiError.INVALID_CREDENTIALS;
    }

    public static InvalidCredentialsException invalidCredentials() {
        return new InvalidCredentialsException();
    }

    public ApiErrorDescriptor error() {
        return error;
    }
}
