package com.legalfam.backend.auth.application.dto;

public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
}
