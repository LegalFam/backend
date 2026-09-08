package com.legalfam.backend.chat.application.event;

import com.legalfam.backend.chat.application.dto.ChatCitationResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatAssistantMessageEvent(
        UUID sessionId,
        UUID messageId,
        String message,
        String language,
        // Solo cuando el idioma en que se respondio no es el que pidio la interfaz. Null en
        // el caso normal; con valor, el frontend explica el cambio.
        String languageRequested,
        String messageLocalized,
        Instant createdAt,
        List<ChatCitationResponse> citations,
        String confidenceStatus,
        String confidenceReason,
        List<String> nextSteps,
        List<String> nextStepsLocalized,
        Boolean specialistSupportRecommended,
        String citationSupportStatus,
        String receiptStatus,
        boolean requiresReceipt
) {
}
