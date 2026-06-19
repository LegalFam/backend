package com.legalfam.backend.chat.domain.exception;

import com.legalfam.backend.common.error.ApiErrorDescriptor;

public class PendingAssistantMessageException extends RuntimeException {
    private final ChatApiError error;

    private PendingAssistantMessageException(ChatApiError error) {
        super(error.message());
        this.error = error;
    }

    public static PendingAssistantMessageException processingPending() {
        return new PendingAssistantMessageException(ChatApiError.MESSAGE_PROCESSING_PENDING);
    }

    public static PendingAssistantMessageException receiptPending() {
        return new PendingAssistantMessageException(ChatApiError.ASSISTANT_RECEIPT_PENDING);
    }

    public ApiErrorDescriptor error() {
        return error;
    }
}
