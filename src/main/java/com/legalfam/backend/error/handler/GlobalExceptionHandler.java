package com.legalfam.backend.error.handler;

import com.legalfam.backend.error.ApiError;
import com.legalfam.backend.error.ApiErrorFactory;
import com.legalfam.backend.error.exception.InvalidRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiError> handleInvalidRequest(InvalidRequestException ex, HttpServletRequest request) {
        log.warn("Invalid request: path={}, message={}", request.getRequestURI(), ex.getMessage());
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
        log.warn("Malformed JSON: path={}", request.getRequestURI());
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "validation_error",
                "malformed_json",
                "Malformed request body",
                request.getRequestURI()
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(HttpServletRequest request) {
        log.warn("Access denied: path={}", request.getRequestURI());
        return buildResponse(
                HttpStatus.FORBIDDEN,
                "authorization_error",
                "forbidden",
                "Access is forbidden",
                request.getRequestURI()
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSizeExceeded(HttpServletRequest request) {
        log.warn("Max upload size exceeded: path={}", request.getRequestURI());
        return buildResponse(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "validation_error",
                "max_upload_size_exceeded",
                "File exceeds configured upload size limit",
                request.getRequestURI()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception: path={}", request.getRequestURI(), ex);
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
