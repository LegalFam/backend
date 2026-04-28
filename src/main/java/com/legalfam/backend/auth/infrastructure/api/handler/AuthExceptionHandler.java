package com.legalfam.backend.auth.infrastructure.api.handler;

import com.legalfam.backend.auth.domain.exception.EmailAlreadyExistsException;
import com.legalfam.backend.auth.domain.exception.InvalidAuthRequestException;
import com.legalfam.backend.auth.domain.exception.InvalidCredentialsException;
import com.legalfam.backend.auth.domain.exception.InvalidRefreshTokenException;
import com.legalfam.backend.common.error.ApiError;
import com.legalfam.backend.common.error.ApiErrorFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.legalfam.backend.auth")
public class AuthExceptionHandler {

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleEmailAlreadyExists(HttpServletRequest request) {
        return buildResponse(
                HttpStatus.CONFLICT,
                "conflict_error",
                "email_already_exists",
                "Email already exists",
                request.getRequestURI()
        );
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(HttpServletRequest request) {
        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                "authentication_error",
                "invalid_credentials",
                "Invalid credentials",
                request.getRequestURI()
        );
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiError> handleInvalidRefreshToken(HttpServletRequest request) {
        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                "authentication_error",
                "invalid_refresh_token",
                "Invalid refresh token",
                request.getRequestURI()
        );
    }

    @ExceptionHandler(InvalidAuthRequestException.class)
    public ResponseEntity<ApiError> handleInvalidAuthRequest(
            InvalidAuthRequestException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "validation_error",
                "invalid_request",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    private ResponseEntity<ApiError> buildResponse(
            HttpStatus status,
            String type,
            String code,
            String message,
            String path
    ) {
        return ResponseEntity.status(status).body(ApiErrorFactory.build(status, type, code, message, path));
    }
}
