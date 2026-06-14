package com.legalfam.backend.chat.application.dto;

import java.util.List;

public record ChatAssistantGatewayResponse(
        String message,
        List<ChatCitationResponse> citations,
        ChatAssistantMetadata metadata
) {
    public ChatAssistantGatewayResponse {
        citations = citations == null ? List.of() : List.copyOf(citations);
        metadata = metadata == null ? ChatAssistantMetadata.empty() : metadata;
    }
}
