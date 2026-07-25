package com.legalfam.backend.chat.infrastructure.api.handler;

import com.legalfam.backend.chat.domain.exception.ChatAccessDeniedException;
import com.legalfam.backend.chat.domain.exception.ChatApiError;
import com.legalfam.backend.chat.domain.exception.ChatNotFoundException;
import com.legalfam.backend.chat.domain.exception.ChatUpstreamException;
import com.legalfam.backend.chat.domain.exception.InsufficientChatTokensException;
import com.legalfam.backend.chat.domain.exception.InvalidChatRequestException;
import com.legalfam.backend.chat.domain.exception.PendingAssistantMessageException;
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

@RestControllerAdvice(basePackages = "com.legalfam.backend.chat")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ChatExceptionHandler {

    @ExceptionHandler(ChatUpstreamException.class)
    public ResponseEntity<ApiError> handleChatUpstream(
            ChatUpstreamException ex,
            HttpServletRequest request
    ) {
        return buildResponse(ex.error(), request);
    }

    @ExceptionHandler(ChatAccessDeniedException.class)
    public ResponseEntity<ApiError> handleChatAccessDenied(
            ChatAccessDeniedException ex,
            HttpServletRequest request
    ) {
        return buildResponse(ex.error(), request);
    }

    @ExceptionHandler(ChatNotFoundException.class)
    public ResponseEntity<ApiError> handleChatNotFound(
            ChatNotFoundException ex,
            HttpServletRequest request
    ) {
        return buildResponse(ex.error(), request);
    }

    @ExceptionHandler(InvalidChatRequestException.class)
    public ResponseEntity<ApiError> handleInvalidChatRequest(
            InvalidChatRequestException ex,
            HttpServletRequest request
    ) {
        return buildResponse(ex.error(), request);
    }

    @ExceptionHandler(PendingAssistantMessageException.class)
    public ResponseEntity<ApiError> handlePendingAssistantMessage(
            PendingAssistantMessageException ex,
            HttpServletRequest request
    ) {
        return buildResponse(ex.error(), request);
    }

    @ExceptionHandler(InsufficientChatTokensException.class)
    public ResponseEntity<ApiError> handleInsufficientChatTokens(
            InsufficientChatTokensException ex,
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
                        .thenComparingInt(ChatExceptionHandler::constraintPriority))
                .findFirst()
                .map(ChatExceptionHandler::mapValidationError)
                .orElse(ChatApiError.CURSOR_INVALID);
        return buildResponse(error, request);
    }

    private ResponseEntity<ApiError> buildResponse(ApiErrorDescriptor error, HttpServletRequest request) {
        return ResponseEntity.status(error.status()).body(ApiErrorFactory.build(error, request.getRequestURI()));
    }

    private static ApiErrorDescriptor mapValidationError(FieldError error) {
        String field = error.getField();
        String constraint = error.getCode() == null ? "" : error.getCode();
        if ("message".equals(field)) {
            return "Size".equals(constraint) ? ChatApiError.MESSAGE_TOO_LONG : ChatApiError.MESSAGE_REQUIRED;
        }
        if ("sessionId".equals(field)) {
            return ChatApiError.SESSION_ID_REQUIRED;
        }
        if ("title".equals(field)) {
            return "Size".equals(constraint) ? ChatApiError.SESSION_TITLE_TOO_LONG : ChatApiError.SESSION_TITLE_REQUIRED;
        }
        if ("rating".equals(field)) {
            return "Min".equals(constraint) || "Max".equals(constraint)
                    ? ChatApiError.RATING_OUT_OF_RANGE
                    : ChatApiError.RATING_REQUIRED;
        }
        if ("comment".equals(field)) {
            return ChatApiError.FEEDBACK_COMMENT_TOO_LONG;
        }
        return ChatApiError.CURSOR_INVALID;
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
