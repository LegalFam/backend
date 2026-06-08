package com.legalfam.backend.chat.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        String role,
        String content,
        Integer rating,
        String feedbackComment,
        Instant feedbackSubmittedAt,
        Instant createdAt,
        List<ChatCitationResponse> citations,
        String confidenceStatus,
        String confidenceReason,
        List<String> clarifyingQuestions,
        List<String> preliminaryActions,
        Boolean specialistSupportRecommended,
        String receiptStatus,
        Instant readAt
) {
}
