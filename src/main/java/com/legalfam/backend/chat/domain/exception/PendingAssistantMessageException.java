package com.legalfam.backend.chat.domain.exception;

public class PendingAssistantMessageException extends RuntimeException {

    public PendingAssistantMessageException(String message) {
        super(message);
    }
}
