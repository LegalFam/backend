package com.legalfam.backend.chat.domain.exception;

import com.legalfam.backend.common.error.ApiErrorDescriptor;

public class InsufficientChatTokensException extends RuntimeException {
    private final ChatApiError error;

    private InsufficientChatTokensException(ChatApiError error) {
        super(error.message());
        this.error = error;
    }

    public static InsufficientChatTokensException noTokens() {
        return new InsufficientChatTokensException(ChatApiError.INSUFFICIENT_TOKENS);
    }

    public ApiErrorDescriptor error() {
        return error;
    }
}
