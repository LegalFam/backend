package com.legalfam.backend.auth.domain.exception;

import com.legalfam.backend.common.error.ApiErrorDescriptor;

public class InvalidAuthRequestException extends RuntimeException {
    private final AuthApiError error;

    private InvalidAuthRequestException(AuthApiError error) {
        super(error.message());
        this.error = error;
    }

    public static InvalidAuthRequestException signupRequestRequired() {
        return new InvalidAuthRequestException(AuthApiError.SIGNUP_REQUEST_REQUIRED);
    }

    public static InvalidAuthRequestException loginRequestRequired() {
        return new InvalidAuthRequestException(AuthApiError.LOGIN_REQUEST_REQUIRED);
    }

    public static InvalidAuthRequestException refreshTokenRequired() {
        return new InvalidAuthRequestException(AuthApiError.REFRESH_TOKEN_REQUIRED);
    }

    public ApiErrorDescriptor error() {
        return error;
    }
}
