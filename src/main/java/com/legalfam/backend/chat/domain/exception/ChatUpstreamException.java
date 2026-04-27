package com.legalfam.backend.chat.domain.exception;

public class ChatUpstreamException extends RuntimeException {
    public ChatUpstreamException(String message) {
        super(message);
    }
}
