package com.legalfam.backend.chat.dto;

import java.util.UUID;
import java.util.List;

public record ChatAskResponse(
        UUID sessionId,
        UUID messageId,
        String message,
        List<ChatCitationResponse> citations
) {
}
