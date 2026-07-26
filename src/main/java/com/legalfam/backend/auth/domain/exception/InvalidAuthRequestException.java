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

    public static InvalidAuthRequestException profileRequestRequired() {
        return new InvalidAuthRequestException(AuthApiError.PROFILE_REQUEST_REQUIRED);
    }

    public static InvalidAuthRequestException passwordRequestRequired() {
        return new InvalidAuthRequestException(AuthApiError.PASSWORD_REQUEST_REQUIRED);
    }

    public static InvalidAuthRequestException currentPasswordInvalid() {
        return new InvalidAuthRequestException(AuthApiError.CURRENT_PASSWORD_INVALID);
    }

    public static InvalidAuthRequestException verifyEmailRequestRequired() {
        return new InvalidAuthRequestException(AuthApiError.VERIFY_EMAIL_REQUEST_REQUIRED);
    }

    public static InvalidAuthRequestException resendVerificationRequestRequired() {
        return new InvalidAuthRequestException(AuthApiError.RESEND_VERIFICATION_REQUEST_REQUIRED);
    }

    public static InvalidAuthRequestException forgotPasswordRequestRequired() {
        return new InvalidAuthRequestException(AuthApiError.FORGOT_PASSWORD_REQUEST_REQUIRED);
    }

    public static InvalidAuthRequestException resetPasswordRequestRequired() {
        return new InvalidAuthRequestException(AuthApiError.RESET_PASSWORD_REQUEST_REQUIRED);
    }

    public static InvalidAuthRequestException tokenRequired() {
        return new InvalidAuthRequestException(AuthApiError.TOKEN_REQUIRED);
    }

    public static InvalidAuthRequestException verificationTokenInvalid() {
        return new InvalidAuthRequestException(AuthApiError.VERIFICATION_TOKEN_INVALID);
    }

    public static InvalidAuthRequestException resetTokenInvalid() {
        return new InvalidAuthRequestException(AuthApiError.RESET_TOKEN_INVALID);
    }

    public ApiErrorDescriptor error() {
        return error;
    }
}
