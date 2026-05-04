package com.legalfam.backend.payment.infrastructure.api.handler;

import com.legalfam.backend.common.error.ApiError;
import com.legalfam.backend.common.error.ApiErrorFactory;
import com.legalfam.backend.payment.domain.exception.InsufficientTokensException;
import com.legalfam.backend.payment.domain.exception.InvalidPaymentRequestException;
import com.legalfam.backend.payment.domain.exception.PaymentGatewayException;
import com.legalfam.backend.payment.domain.exception.PaymentWebhookException;
import com.legalfam.backend.payment.domain.exception.SubscriptionInactiveException;
import com.legalfam.backend.payment.domain.exception.SubscriptionNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.legalfam.backend.payment")
public class PaymentExceptionHandler {

    @ExceptionHandler(InvalidPaymentRequestException.class)
    public ResponseEntity<ApiError> handleInvalidPaymentRequest(
            InvalidPaymentRequestException ex,
            HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.BAD_REQUEST, "validation_error", "invalid_request", ex.getMessage(), request);
    }

    @ExceptionHandler(SubscriptionNotFoundException.class)
    public ResponseEntity<ApiError> handleSubscriptionNotFound(
            SubscriptionNotFoundException ex,
            HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.NOT_FOUND, "payment_error", "subscription_not_found", ex.getMessage(), request);
    }

    @ExceptionHandler({SubscriptionInactiveException.class, InsufficientTokensException.class})
    public ResponseEntity<ApiError> handleSubscriptionAccess(RuntimeException ex, HttpServletRequest request) {
        String code = ex instanceof InsufficientTokensException ? "insufficient_tokens" : "subscription_inactive";
        return buildResponse(HttpStatus.FORBIDDEN, "payment_error", code, ex.getMessage(), request);
    }

    @ExceptionHandler(PaymentWebhookException.class)
    public ResponseEntity<ApiError> handleWebhookError(PaymentWebhookException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "payment_error", "invalid_webhook", ex.getMessage(), request);
    }

    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<ApiError> handleGatewayError(PaymentGatewayException ex, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "payment_error",
                "payment_gateway_unavailable",
                ex.getMessage(),
                request
        );
    }

    private ResponseEntity<ApiError> buildResponse(
            HttpStatus status,
            String type,
            String code,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status)
                .body(ApiErrorFactory.build(status, type, code, message, request.getRequestURI()));
    }
}
