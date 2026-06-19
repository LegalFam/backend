package com.legalfam.backend.common.error.handler;

import com.legalfam.backend.common.error.ApiError;
import com.legalfam.backend.common.error.ApiErrorDescriptor;
import com.legalfam.backend.common.error.ApiErrorFactory;
import com.legalfam.backend.common.error.CommonApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedJson(HttpServletRequest request) {
        log.warn("Malformed JSON: path={}", request.getRequestURI());
        return buildResponse(CommonApiError.MALFORMED_JSON, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationFailure(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        ApiErrorDescriptor error = ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField)
                        .thenComparingInt(GlobalExceptionHandler::constraintPriority))
                .findFirst()
                .map(GlobalExceptionHandler::mapValidationError)
                .orElse(CommonApiError.INVALID_REQUEST);
        log.warn("Request validation failed: path={}, code={}", request.getRequestURI(), error.code());
        return buildResponse(error, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(HttpServletRequest request) {
        log.warn("Access denied: path={}", request.getRequestURI());
        return buildResponse(CommonApiError.FORBIDDEN, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSizeExceeded(HttpServletRequest request) {
        log.warn("Max upload size exceeded: path={}", request.getRequestURI());
        return buildResponse(CommonApiError.MAX_UPLOAD_SIZE_EXCEEDED, request);
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> handleAsyncRequestNotUsable(
            AsyncRequestNotUsableException ex,
            HttpServletRequest request
    ) {
        log.debug("Async request closed by client: path={}, message={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception: path={}", request.getRequestURI(), ex);
        return buildResponse(CommonApiError.INTERNAL_SERVER_ERROR, request);
    }

    private ResponseEntity<ApiError> buildResponse(ApiErrorDescriptor error, HttpServletRequest request) {
        return ResponseEntity.status(error.status()).body(ApiErrorFactory.build(error, request.getRequestURI()));
    }

    private static int constraintPriority(FieldError error) {
        return switch (error.getCode() == null ? "" : error.getCode()) {
            case "NotBlank", "NotEmpty", "NotNull" -> 0;
            case "Email", "Pattern" -> 1;
            case "Size", "Min", "Max" -> 2;
            default -> 3;
        };
    }

    private static ApiErrorDescriptor mapValidationError(FieldError error) {
        return CommonApiError.INVALID_REQUEST;
    }
}
