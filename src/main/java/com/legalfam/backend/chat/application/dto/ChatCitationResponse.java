package com.legalfam.backend.chat.application.dto;

public record ChatCitationResponse(
        String sourceTitle,
        String sourceSnippet,
        String sourceUrl
) {
}
