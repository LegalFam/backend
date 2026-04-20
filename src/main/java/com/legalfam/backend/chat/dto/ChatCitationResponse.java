package com.legalfam.backend.chat.dto;

public record ChatCitationResponse(
        String sourceTitle,
        String sourceSnippet,
        String sourceUrl
) {
}
