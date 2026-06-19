package com.legalfam.backend.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

public class RefreshToken {

    private UUID id;
    private String token;
    private Instant expiresAt;
    private boolean revoked;
    private UUID userId;

    public static RefreshToken issue(String tokenHash, UUID userId, Instant expiresAt) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.token = tokenHash;
        refreshToken.userId = userId;
        refreshToken.expiresAt = expiresAt;
        refreshToken.revoked = false;
        return refreshToken;
    }

    public static RefreshToken restore(UUID id, String tokenHash, Instant expiresAt, boolean revoked, UUID userId) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.id = id;
        refreshToken.token = tokenHash;
        refreshToken.expiresAt = expiresAt;
        refreshToken.revoked = revoked;
        refreshToken.userId = userId;
        return refreshToken;
    }

    public UUID getId() {
        return id;
    }
    public String getToken() {
        return token;
    }
    public Instant getExpiresAt() {
        return expiresAt;
    }
    public boolean isRevoked() {
        return revoked;
    }
    public UUID getUserId() {
        return userId;
    }
    public boolean canBeRotatedAt(Instant now) {
        return !revoked && expiresAt != null && !expiresAt.isBefore(now);
    }
    public void revoke() {
        revoked = true;
    }
}
