package com.legalfam.backend.auth.application.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
    public TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
        this(accessToken, refreshToken, tokenType, expiresIn, null);
    }
}
