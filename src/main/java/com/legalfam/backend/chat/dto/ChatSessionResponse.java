package com.legalfam.backend.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatSessionResponse(
        UUID id,
        Instant createdAt,
        Instant updatedAt
) {
}
