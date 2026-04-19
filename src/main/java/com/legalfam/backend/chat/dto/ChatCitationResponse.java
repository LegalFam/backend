package com.legalfam.backend.chat.dto;

public record ChatCitationResponse(
        String fileId,
        String fileName,
        String snippet
) {
}
