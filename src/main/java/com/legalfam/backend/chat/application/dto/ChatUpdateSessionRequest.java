package com.legalfam.backend.chat.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatUpdateSessionRequest(
        @NotBlank(message = "Session title is required")
        @Size(max = 120, message = "Session title must be at most 120 characters")
        String title
) {
}
