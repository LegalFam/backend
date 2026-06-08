package com.legalfam.backend.chat.application.dto;

import java.util.List;

public record ChatAssistantMetadata(
        String confidenceStatus,
        String confidenceReason,
        List<String> nextSteps,
        Boolean specialistSupportRecommended
) {
    public static ChatAssistantMetadata empty() {
        return new ChatAssistantMetadata(null, null, List.of(), null);
    }
}
