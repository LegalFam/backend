package com.legalfam.backend.chat.domain.exception;

import com.legalfam.backend.common.error.ApiErrorDescriptor;

public class InvalidChatRequestException extends RuntimeException {
    private final ChatApiError error;

    private InvalidChatRequestException(ChatApiError error) {
        super(error.message());
        this.error = error;
    }

    public static InvalidChatRequestException of(ChatApiError error) {
        return new InvalidChatRequestException(error);
    }

    public ApiErrorDescriptor error() {
        return error;
    }
}
