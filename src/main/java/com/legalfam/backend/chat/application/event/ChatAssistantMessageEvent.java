package com.legalfam.backend.chat.application.event;

import com.legalfam.backend.chat.application.dto.ChatCitationResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatAssistantMessageEvent(
        UUID sessionId,
        UUID messageId,
        String message,
        Instant createdAt,
        List<ChatCitationResponse> citations,
        String confidenceStatus,
        String confidenceReason,
        List<String> nextSteps,
        Boolean specialistSupportRecommended,
        String receiptStatus,
        boolean requiresReceipt
) {
}
