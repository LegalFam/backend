package com.legalfam.backend.chat.event;

import java.util.UUID;

public record ChatMessageQueuedEvent(
        UUID chatSessionId,
        String userMessageInput
) {
}

