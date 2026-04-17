package com.legalfam.backend.conversation.dto;

import java.util.List;

public record ConversationAskResponse(
        String answer,
        List<ConversationCitationResponse> citations
) {
}
