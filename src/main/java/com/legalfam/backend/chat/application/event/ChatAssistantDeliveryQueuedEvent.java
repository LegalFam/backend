package com.legalfam.backend.chat.application.event;

import java.util.UUID;

public record ChatAssistantDeliveryQueuedEvent(
        UUID userId,
        UUID chatSessionId,
        UUID assistantMessageId,
        ChatAssistantMessageEvent event,
        ChatAssistantErrorEvent error
) {
    public static ChatAssistantDeliveryQueuedEvent ofMessage(UUID userId, ChatAssistantMessageEvent event) {
        return new ChatAssistantDeliveryQueuedEvent(userId, event.sessionId(), event.messageId(), event, null);
    }

    public static ChatAssistantDeliveryQueuedEvent ofError(UUID userId, ChatAssistantErrorEvent error) {
        return new ChatAssistantDeliveryQueuedEvent(userId, error.sessionId(), error.messageId(), null, error);
    }
}
