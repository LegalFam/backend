package com.legalfam.backend.chat.event;

import java.time.Instant;
import java.util.UUID;

public record ChatAssistantErrorEvent(
        UUID sessionId,
        UUID messageId,
        String errorCode,
        String errorMessage,
        Instant createdAt
) {
}
