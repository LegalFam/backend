package com.legalfam.backend.auth.domain.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    @Test
    void issuedTokenCanBeRotatedUntilItIsRevokedOrExpired() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        RefreshToken token = RefreshToken.issue(
                "hash",
                UUID.randomUUID(),
                now.plusSeconds(60)
        );

        assertTrue(token.canBeRotatedAt(now));

        token.revoke();

        assertFalse(token.canBeRotatedAt(now));
    }

    @Test
    void expiredTokenCannotBeRotated() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        RefreshToken token = RefreshToken.issue(
                "hash",
                UUID.randomUUID(),
                now.minusSeconds(1)
        );

        assertFalse(token.canBeRotatedAt(now));
    }
}
