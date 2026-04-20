package com.legalfam.backend.chat.dto;

import java.util.UUID;

public record ChatAskRequest(
        String message,
        UUID sessionId
) {
}
