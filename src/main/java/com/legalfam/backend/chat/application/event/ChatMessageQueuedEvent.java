package com.legalfam.backend.chat.application.event;

import java.util.UUID;

public record ChatMessageQueuedEvent(
        UUID chatSessionId,
        String userMessageInput
) {
}

