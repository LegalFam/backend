package com.legalfam.backend.payment.infrastructure.api.handler;

import com.legalfam.backend.common.error.ApiError;
import com.legalfam.backend.common.error.ApiErrorDescriptor;
import com.legalfam.backend.common.error.ApiErrorFactory;
import com.legalfam.backend.payment.domain.exception.InsufficientTokensException;
import com.legalfam.backend.payment.domain.exception.InvalidPaymentRequestException;
import com.legalfam.backend.payment.domain.exception.PaymentApiError;
import com.legalfam.backend.payment.domain.exception.PaymentGatewayException;
import com.legalfam.backend.payment.domain.exception.PaymentWebhookException;
import com.legalfam.backend.payment.domain.exception.SubscriptionInactiveException;
import com.legalfam.backend.payment.domain.exception.SubscriptionNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.legalfam.backend.payment")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PaymentExceptionHandler {

    @ExceptionHandler(InvalidPaymentRequestException.class)
    public ResponseEntity<ApiError> handleInvalidPaymentRequest(
            InvalidPaymentRequestException ex,
            HttpServletRequest request
    ) {
        return buildResponse(ex.error(), request);
    }

    @ExceptionHandler(SubscriptionNotFoundException.class)
    public ResponseEntity<ApiError> handleSubscriptionNotFound(
            SubscriptionNotFoundException ex,
            HttpServletRequest request
    ) {
        return buildResponse(ex.error(), request);
    }

    @ExceptionHandler({SubscriptionInactiveException.class, InsufficientTokensException.class})
    public ResponseEntity<ApiError> handleSubscriptionAccess(RuntimeException ex, HttpServletRequest request) {
        ApiErrorDescriptor error = ex instanceof InsufficientTokensException
                ? ((InsufficientTokensException) ex).error()
                : ((SubscriptionInactiveException) ex).error();
        return buildResponse(error, request);
    }

    @ExceptionHandler(PaymentWebhookException.class)
    public ResponseEntity<ApiError> handleWebhookError(PaymentWebhookException ex, HttpServletRequest request) {
        return buildResponse(ex.error(), request);
    }

    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<ApiError> handleGatewayError(PaymentGatewayException ex, HttpServletRequest request) {
        return buildResponse(ex.error(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationFailure(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        ApiErrorDescriptor error = ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField)
                        .thenComparingInt(PaymentExceptionHandler::constraintPriority))
                .findFirst()
                .map(PaymentExceptionHandler::mapValidationError)
                .orElse(PaymentApiError.CHECKOUT_REQUEST_REQUIRED);
        return buildResponse(error, request);
    }

    private ResponseEntity<ApiError> buildResponse(ApiErrorDescriptor error, HttpServletRequest request) {
        return ResponseEntity.status(error.status())
                .body(ApiErrorFactory.build(error, request.getRequestURI()));
    }

    private static ApiErrorDescriptor mapValidationError(FieldError error) {
        String field = error.getField();
        String constraint = error.getCode() == null ? "" : error.getCode();
        if ("planCode".equals(field)) {
            return "Size".equals(constraint) ? PaymentApiError.PLAN_CODE_TOO_LONG : PaymentApiError.PLAN_CODE_REQUIRED;
        }
        if ("successUrl".equals(field)) {
            return "Pattern".equals(constraint) ? PaymentApiError.SUCCESS_URL_INVALID : PaymentApiError.SUCCESS_URL_TOO_LONG;
        }
        if ("cancelUrl".equals(field)) {
            return "Pattern".equals(constraint) ? PaymentApiError.CANCEL_URL_INVALID : PaymentApiError.CANCEL_URL_TOO_LONG;
        }
        return PaymentApiError.CHECKOUT_REQUEST_REQUIRED;
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
