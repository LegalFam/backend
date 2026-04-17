package com.legalfam.backend.conversation.dto;

public record ConversationCitationResponse(
        String fileId,
        String fileName,
        String snippet
) {
}
