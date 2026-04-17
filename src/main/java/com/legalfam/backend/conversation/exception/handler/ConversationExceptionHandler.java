package com.legalfam.backend.conversation.exception.handler;

import com.legalfam.backend.conversation.exception.ConversationUpstreamException;
import com.legalfam.backend.error.ApiError;
import com.legalfam.backend.error.ApiErrorFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.legalfam.backend.conversation")
public class ConversationExceptionHandler {

    @ExceptionHandler(ConversationUpstreamException.class)
    public ResponseEntity<ApiError> handleConversationUpstream(
            ConversationUpstreamException ex,
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
