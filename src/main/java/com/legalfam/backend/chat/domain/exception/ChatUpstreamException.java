package com.legalfam.backend.chat.domain.exception;

import com.legalfam.backend.common.error.ApiErrorDescriptor;
import java.util.Locale;

public class ChatUpstreamException extends RuntimeException {
    private final ChatApiError error;

    private ChatUpstreamException() {
        this(ChatApiError.UPSTREAM_ERROR);
    }

    private ChatUpstreamException(ChatApiError error) {
        super(error.message());
        this.error = error;
    }

    public static ChatUpstreamException upstreamError() {
        return new ChatUpstreamException();
    }

    public static ChatUpstreamException of(ChatApiError error) {
        return new ChatUpstreamException(error);
    }

    public ApiErrorDescriptor error() {
        return error;
    }

    public String code() {
        return error.code();
    }

    public static ChatUpstreamException fromExternalCode(String code) {
        if (code == null || code.isBlank()) {
            return upstreamError();
        }
        return of(switch (code.trim().toLowerCase(Locale.ROOT)) {
            case "upstream_timeout" -> ChatApiError.UPSTREAM_TIMEOUT;
            case "upstream_empty_response" -> ChatApiError.UPSTREAM_EMPTY_RESPONSE;
            case "upstream_invalid_response" -> ChatApiError.UPSTREAM_INVALID_RESPONSE;
            case "upstream_not_configured" -> ChatApiError.UPSTREAM_NOT_CONFIGURED;
            case "upstream_unavailable" -> ChatApiError.UPSTREAM_UNAVAILABLE;
            case "upstream_request_invalid" -> ChatApiError.UPSTREAM_REQUEST_INVALID;
            case "agent_validation_failed" -> ChatApiError.AGENT_VALIDATION_FAILED;
            default -> ChatApiError.UPSTREAM_ERROR;
        });
    }
}
