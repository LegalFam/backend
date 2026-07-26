package com.legalfam.backend.auth.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendVerificationRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Valid email is required")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email
) {
}
