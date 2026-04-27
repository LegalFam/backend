package com.legalfam.backend.user.application.dto;

import java.util.UUID;

public record UserResponse(UUID id, String email, String name, String phone) {
}
