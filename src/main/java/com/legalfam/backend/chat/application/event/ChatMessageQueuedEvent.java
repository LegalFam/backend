package com.legalfam.backend.chat.application.event;

import com.legalfam.backend.chat.application.dto.ChatPreviousMessage;
import java.util.List;
import java.util.UUID;

public record ChatMessageQueuedEvent(
        UUID chatSessionId,
        UUID userMessageId,
        String userMessageInput,
        List<ChatPreviousMessage> previousMessages
) {
    public ChatMessageQueuedEvent {
        previousMessages = previousMessages == null ? List.of() : List.copyOf(previousMessages);
    }
}

