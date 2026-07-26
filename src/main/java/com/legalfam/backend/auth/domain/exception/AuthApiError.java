package com.legalfam.backend.auth.domain.exception;

import com.legalfam.backend.common.error.ApiErrorDescriptor;

public enum AuthApiError implements ApiErrorDescriptor {
    EMAIL_REQUIRED("validation_error", "email_required", 400, "Email is required"),
    EMAIL_INVALID("validation_error", "email_invalid", 400, "Valid email is required"),
    EMAIL_TOO_LONG("validation_error", "email_too_long", 400, "Email must be at most 255 characters"),
    PASSWORD_REQUIRED("validation_error", "password_required", 400, "Password is required"),
    PASSWORD_LENGTH_INVALID("validation_error", "password_length_invalid", 400, "Password length is invalid"),
    NAME_REQUIRED("validation_error", "name_required", 400, "Name is required"),
    NAME_TOO_LONG("validation_error", "name_too_long", 400, "Name must be at most 120 characters"),
    PHONE_REQUIRED("validation_error", "phone_required", 400, "Phone is required"),
    PHONE_TOO_LONG("validation_error", "phone_too_long", 400, "Phone must be at most 30 characters"),
    REFRESH_TOKEN_REQUIRED("validation_error", "refresh_token_required", 400, "Refresh token is required"),
    EMAIL_ALREADY_EXISTS("conflict_error", "email_already_exists", 409, "Email already exists"),
    INVALID_CREDENTIALS("authentication_error", "invalid_credentials", 401, "Invalid credentials"),
    INVALID_REFRESH_TOKEN("authentication_error", "invalid_refresh_token", 401, "Invalid refresh token"),
    SIGNUP_REQUEST_REQUIRED("validation_error", "signup_request_required", 400, "Signup request body is required"),
    LOGIN_REQUEST_REQUIRED("validation_error", "login_request_required", 400, "Login request body is required"),
    PROFILE_REQUEST_REQUIRED("validation_error", "profile_request_required", 400, "Profile request body is required"),
    PASSWORD_REQUEST_REQUIRED("validation_error", "password_request_required", 400, "Password request body is required"),
    CURRENT_PASSWORD_INVALID("validation_error", "current_password_invalid", 400, "Current password is invalid"),
    EMAIL_NOT_VERIFIED("authorization_error", "email_not_verified", 403, "Email is not verified"),
    EMAIL_ALREADY_VERIFIED("conflict_error", "email_already_verified", 409, "Email is already verified"),
    TOKEN_REQUIRED("validation_error", "token_required", 400, "Token is required"),
    VERIFICATION_TOKEN_INVALID("validation_error", "verification_token_invalid", 400,
            "Verification token is invalid or expired"),
    RESET_TOKEN_INVALID("validation_error", "reset_token_invalid", 400, "Reset token is invalid or expired"),
    VERIFY_EMAIL_REQUEST_REQUIRED("validation_error", "verify_email_request_required", 400,
            "Verify email request body is required"),
    RESEND_VERIFICATION_REQUEST_REQUIRED("validation_error", "resend_verification_request_required", 400,
            "Resend verification request body is required"),
    FORGOT_PASSWORD_REQUEST_REQUIRED("validation_error", "forgot_password_request_required", 400,
            "Forgot password request body is required"),
    RESET_PASSWORD_REQUEST_REQUIRED("validation_error", "reset_password_request_required", 400,
            "Reset password request body is required");

    private final String type;
    private final String code;
    private final int status;
    private final String message;

    AuthApiError(String type, String code, int status, String message) {
        this.type = type;
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public String type() { return type; }
    public String code() { return code; }
    public int status() { return status; }
    public String message() { return message; }
}
