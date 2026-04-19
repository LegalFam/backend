package com.legalfam.backend.chat.dto;

import java.util.List;

public record ChatAskResponse(
        String message,
        List<ChatCitationResponse> citations
) {
}
