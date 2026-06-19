package com.legalfam.backend.auth.domain.exception;

import com.legalfam.backend.common.error.ApiErrorDescriptor;

public class EmailAlreadyExistsException extends RuntimeException {
    private final AuthApiError error;

    private EmailAlreadyExistsException(String email) {
        super(AuthApiError.EMAIL_ALREADY_EXISTS.message());
        this.error = AuthApiError.EMAIL_ALREADY_EXISTS;
    }

    public static EmailAlreadyExistsException forEmail(String email) {
        return new EmailAlreadyExistsException(email);
    }

    public ApiErrorDescriptor error() {
        return error;
    }
}
