package com.legalfam.backend.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

public class OneTimeToken {

    private UUID id;
    private String tokenHash;
    private OneTimeTokenPurpose purpose;
    private UUID userId;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant consumedAt;

    public static OneTimeToken issue(
            String tokenHash,
            OneTimeTokenPurpose purpose,
            UUID userId,
            Instant createdAt,
            Instant expiresAt
    ) {
        OneTimeToken token = new OneTimeToken();
        token.tokenHash = tokenHash;
        token.purpose = purpose;
        token.userId = userId;
        token.createdAt = createdAt;
        token.expiresAt = expiresAt;
        return token;
    }

    public static OneTimeToken restore(
            UUID id,
            String tokenHash,
            OneTimeTokenPurpose purpose,
            UUID userId,
            Instant createdAt,
            Instant expiresAt,
            Instant consumedAt
    ) {
        OneTimeToken token = new OneTimeToken();
        token.id = id;
        token.tokenHash = tokenHash;
        token.purpose = purpose;
        token.userId = userId;
        token.createdAt = createdAt;
        token.expiresAt = expiresAt;
        token.consumedAt = consumedAt;
        return token;
    }

    public boolean isUsableAt(Instant now) {
        return consumedAt == null && expiresAt != null && !expiresAt.isBefore(now);
    }

    public void consume(Instant now) {
        if (consumedAt == null) {
            consumedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }
    public String getTokenHash() {
        return tokenHash;
    }
    public OneTimeTokenPurpose getPurpose() {
        return purpose;
    }
    public UUID getUserId() {
        return userId;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
    public Instant getExpiresAt() {
        return expiresAt;
    }
    public Instant getConsumedAt() {
        return consumedAt;
    }
}
