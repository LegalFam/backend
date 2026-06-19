package com.legalfam.backend.chat.domain.exception;

import com.legalfam.backend.common.error.ApiErrorDescriptor;

public class ChatNotFoundException extends RuntimeException {
    private final ChatApiError error;

    private ChatNotFoundException(ChatApiError error) {
        super(error.message());
        this.error = error;
    }

    public static ChatNotFoundException session() {
        return new ChatNotFoundException(ChatApiError.CHAT_SESSION_NOT_FOUND);
    }

    public static ChatNotFoundException message() {
        return new ChatNotFoundException(ChatApiError.CHAT_MESSAGE_NOT_FOUND);
    }

    public static ChatNotFoundException assistantDeliveryEvent() {
        return new ChatNotFoundException(ChatApiError.ASSISTANT_DELIVERY_EVENT_NOT_FOUND);
    }

    public ApiErrorDescriptor error() {
        return error;
    }
}
