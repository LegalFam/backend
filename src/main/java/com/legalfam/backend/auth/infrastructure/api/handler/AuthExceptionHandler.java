package com.legalfam.backend.auth.infrastructure.api.handler;

import com.legalfam.backend.auth.domain.exception.EmailAlreadyExistsException;
import com.legalfam.backend.auth.domain.exception.AuthApiError;
import com.legalfam.backend.auth.domain.exception.InvalidAuthRequestException;
import com.legalfam.backend.auth.domain.exception.InvalidCredentialsException;
import com.legalfam.backend.auth.domain.exception.InvalidRefreshTokenException;
import com.legalfam.backend.common.error.ApiError;
import com.legalfam.backend.common.error.ApiErrorDescriptor;
import com.legalfam.backend.common.error.ApiErrorFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.legalfam.backend.auth")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleEmailAlreadyExists(HttpServletRequest request) {
        return buildResponse(AuthApiError.EMAIL_ALREADY_EXISTS, request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(HttpServletRequest request) {
        return buildResponse(AuthApiError.INVALID_CREDENTIALS, request);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiError> handleInvalidRefreshToken(HttpServletRequest request) {
        return buildResponse(AuthApiError.INVALID_REFRESH_TOKEN, request);
    }

    @ExceptionHandler(InvalidAuthRequestException.class)
    public ResponseEntity<ApiError> handleInvalidAuthRequest(
            InvalidAuthRequestException ex,
            HttpServletRequest request
    ) {
        return buildResponse(ex.error(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationFailure(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        ApiErrorDescriptor error = ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField)
                        .thenComparingInt(AuthExceptionHandler::constraintPriority))
                .findFirst()
                .map(AuthExceptionHandler::mapValidationError)
                .orElse(AuthApiError.SIGNUP_REQUEST_REQUIRED);
        return buildResponse(error, request);
    }

    private ResponseEntity<ApiError> buildResponse(ApiErrorDescriptor error, HttpServletRequest request) {
        return ResponseEntity.status(error.status()).body(ApiErrorFactory.build(error, request.getRequestURI()));
    }

    private static ApiErrorDescriptor mapValidationError(FieldError error) {
        String field = error.getField();
        String constraint = error.getCode() == null ? "" : error.getCode();
        if ("email".equals(field)) {
            return switch (constraint) {
                case "NotBlank", "NotEmpty", "NotNull" -> AuthApiError.EMAIL_REQUIRED;
                case "Email" -> AuthApiError.EMAIL_INVALID;
                case "Size" -> AuthApiError.EMAIL_TOO_LONG;
                default -> AuthApiError.EMAIL_REQUIRED;
            };
        }
        if ("password".equals(field) || "currentPassword".equals(field) || "newPassword".equals(field)) {
            return "Size".equals(constraint)
                    ? AuthApiError.PASSWORD_LENGTH_INVALID
                    : AuthApiError.PASSWORD_REQUIRED;
        }
        if ("name".equals(field)) {
            return "Size".equals(constraint) ? AuthApiError.NAME_TOO_LONG : AuthApiError.NAME_REQUIRED;
        }
        if ("phone".equals(field)) {
            return "Size".equals(constraint) ? AuthApiError.PHONE_TOO_LONG : AuthApiError.PHONE_REQUIRED;
        }
        if ("refreshToken".equals(field)) {
            return AuthApiError.REFRESH_TOKEN_REQUIRED;
        }
        return AuthApiError.SIGNUP_REQUEST_REQUIRED;
    }

    private static int constraintPriority(FieldError error) {
        return switch (error.getCode() == null ? "" : error.getCode()) {
            case "NotBlank", "NotEmpty", "NotNull" -> 0;
            case "Email", "Pattern" -> 1;
            case "Size", "Min", "Max" -> 2;
            default -> 3;
        };
    }
}
