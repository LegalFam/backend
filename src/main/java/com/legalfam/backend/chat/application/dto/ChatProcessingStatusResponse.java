package com.legalfam.backend.chat.application.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatProcessingStatusResponse(
        boolean processing,
        UUID chatSessionId,
        UUID userMessageId,
        String status,
        Instant updatedAt
) {
    public static ChatProcessingStatusResponse idle() {
        return new ChatProcessingStatusResponse(false, null, null, null, null);
    }
}
