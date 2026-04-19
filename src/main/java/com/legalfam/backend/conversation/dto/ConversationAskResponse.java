package com.legalfam.backend.conversation.dto;

import java.util.List;

public record ConversationAskResponse(
        String message,
        List<ConversationCitationResponse> citations
) {
}
