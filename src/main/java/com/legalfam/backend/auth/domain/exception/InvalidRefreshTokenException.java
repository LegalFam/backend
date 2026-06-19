package com.legalfam.backend.auth.domain.exception;

import com.legalfam.backend.common.error.ApiErrorDescriptor;

public class InvalidRefreshTokenException extends RuntimeException {
    private final AuthApiError error;

    private InvalidRefreshTokenException() {
        super(AuthApiError.INVALID_REFRESH_TOKEN.message());
        this.error = AuthApiError.INVALID_REFRESH_TOKEN;
    }

    public static InvalidRefreshTokenException invalidRefreshToken() {
        return new InvalidRefreshTokenException();
    }

    public ApiErrorDescriptor error() {
        return error;
    }
}
