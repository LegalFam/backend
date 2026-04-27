package com.legalfam.backend.chat.application.dto;

import java.util.UUID;

public record ChatSendAcceptedResponse(
        UUID sessionId,
        UUID userMessageId,
        String status
) {
}
