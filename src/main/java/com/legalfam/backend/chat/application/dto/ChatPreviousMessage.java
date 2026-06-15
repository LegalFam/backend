package com.legalfam.backend.chat.application.dto;

import java.time.Instant;

public record ChatPreviousMessage(
        String role,
        String content,
        Instant createdAt
) {
}
