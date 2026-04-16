package com.legalfam.backend.error.handler;

import com.legalfam.backend.error.ApiError;
import com.legalfam.backend.error.ApiErrorFactory;
import com.legalfam.backend.error.exception.InvalidRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiError> handleInvalidRequest(InvalidRequestException ex, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "validation_error",
                "invalid_request",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedJson(HttpServletRequest request) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "validation_error",
                "malformed_json",
                "Malformed request body",
                request.getRequestURI()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedException(HttpServletRequest request) {
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal_error",
                "internal_server_error",
                "An unexpected error occurred",
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
