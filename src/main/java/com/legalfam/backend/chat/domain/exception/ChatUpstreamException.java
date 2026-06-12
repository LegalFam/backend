package com.legalfam.backend.chat.domain.exception;

public class ChatUpstreamException extends RuntimeException {
    private final String code;

    public ChatUpstreamException(String message) {
        this("UPSTREAM_ERROR", message);
    }

    public ChatUpstreamException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
