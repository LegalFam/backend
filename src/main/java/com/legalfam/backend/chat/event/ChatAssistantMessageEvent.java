package com.legalfam.backend.chat.event;

import com.legalfam.backend.chat.dto.ChatCitationResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatAssistantMessageEvent(
        UUID sessionId,
        UUID messageId,
        String message,
        Instant createdAt,
        List<ChatCitationResponse> citations
) {
}
