package com.legalfam.backend.chat.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        String role,
        // Espanol canonico. El frontend lo usa como respaldo y para el conmutador
        // "Ver en espanol".
        String content,
        String language,
        // Texto en la lengua del usuario. Null en conversaciones en espanol.
        String contentLocalized,
        String errorCode,
        Integer rating,
        String feedbackComment,
        Instant feedbackSubmittedAt,
        Instant createdAt,
        List<ChatCitationResponse> citations,
        String confidenceStatus,
        String confidenceReason,
        List<String> nextSteps,
        List<String> nextStepsLocalized,
        Boolean specialistSupportRecommended,
        String citationSupportStatus,
        String receiptStatus,
        Instant readAt
) {
}
