package com.legalfam.backend.chat.application.dto;

import java.util.List;

public record ChatAssistantMetadata(
        String confidenceStatus,
        String confidenceReason,
        List<String> nextSteps,
        List<String> nextStepsLocalized,
        Boolean specialistSupportRecommended,
        String citationSupportStatus,
        Integer agentTokenCost
) {
    public static ChatAssistantMetadata empty() {
        return new ChatAssistantMetadata(null, null, List.of(), List.of(), null, null, null);
    }
}
