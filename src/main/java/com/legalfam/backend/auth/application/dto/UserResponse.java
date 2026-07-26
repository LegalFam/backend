package com.legalfam.backend.auth.application.dto;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String name,
        String phone,
        boolean emailVerified
) {
}
