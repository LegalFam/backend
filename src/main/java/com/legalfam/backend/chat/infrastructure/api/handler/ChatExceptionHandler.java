package com.legalfam.backend.chat.infrastructure.api.handler;

import com.legalfam.backend.chat.domain.exception.ChatAccessDeniedException;
import com.legalfam.backend.chat.domain.exception.ChatNotFoundException;
import com.legalfam.backend.chat.domain.exception.ChatUpstreamException;
import com.legalfam.backend.common.error.ApiError;
import com.legalfam.backend.common.error.ApiErrorFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.legalfam.backend.chat")
public class ChatExceptionHandler {

    @ExceptionHandler(ChatUpstreamException.class)
    public ResponseEntity<ApiError> handleChatUpstream(
            ChatUpstreamException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_GATEWAY,
                "upstream_error",
                "upstream_service_unavailable",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(ChatAccessDeniedException.class)
    public ResponseEntity<ApiError> handleChatAccessDenied(
            ChatAccessDeniedException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.FORBIDDEN,
                "authorization_error",
                "forbidden",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(ChatNotFoundException.class)
    public ResponseEntity<ApiError> handleChatNotFound(
            ChatNotFoundException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "resource_error",
                "not_found",
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
