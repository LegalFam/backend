package com.legalfam.backend.chat.application.event;

import java.util.UUID;

public record ChatAssistantDeliveryQueuedEvent(
        UUID userId,
        UUID chatSessionId,
        UUID assistantMessageId,
        ChatAssistantMessageEvent event
) {
}
