package com.legalfam.backend.chat.application.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatSessionResponse(
        UUID id,
        String title,
        Instant createdAt,
        Instant updatedAt
) {
    public ChatSessionResponse(UUID id, Instant createdAt, Instant updatedAt) {
        this(id, null, createdAt, updatedAt);
    }
}
