package com.legalfam.backend.auth.exception.handler;

import com.legalfam.backend.auth.exception.EmailAlreadyExistsException;
import com.legalfam.backend.auth.exception.InvalidCredentialsException;
import com.legalfam.backend.auth.exception.InvalidRefreshTokenException;
import com.legalfam.backend.error.ApiError;
import com.legalfam.backend.error.ApiErrorFactory;
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
