package com.legalfam.backend.chat.dto;

import java.util.UUID;

public record ChatSendAcceptedResponse(
        UUID sessionId,
        UUID userMessageId,
        String status
) {
}
