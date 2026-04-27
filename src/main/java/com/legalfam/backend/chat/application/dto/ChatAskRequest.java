package com.legalfam.backend.chat.application.dto;

import java.util.UUID;

public record ChatAskRequest(
        String message,
        UUID sessionId
) {
}
