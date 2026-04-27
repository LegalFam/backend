package com.legalfam.backend.chat.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        String role,
        String content,
        Integer rating,
        Instant createdAt,
        List<ChatCitationResponse> citations
) {
}
