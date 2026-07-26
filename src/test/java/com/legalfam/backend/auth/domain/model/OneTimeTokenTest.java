package com.legalfam.backend.auth.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OneTimeTokenTest {

    @Test
    void freshTokenIsUsable() {
        Instant now = Instant.now();
        OneTimeToken token = issue(now, now.plusSeconds(3600));

        assertTrue(token.isUsableAt(now));
        assertNull(token.getConsumedAt());
    }

    @Test
    void expiredTokenIsNotUsable() {
        Instant now = Instant.now();
        OneTimeToken token = issue(now.minusSeconds(7200), now.minusSeconds(1));

        assertFalse(token.isUsableAt(now));
    }

    @Test
    void tokenIsUsableExactlyAtExpiry() {
        Instant now = Instant.now();
        OneTimeToken token = issue(now.minusSeconds(60), now);

        assertTrue(token.isUsableAt(now));
    }

    @Test
    void consumedTokenIsNotUsable() {
        Instant now = Instant.now();
        OneTimeToken token = issue(now, now.plusSeconds(3600));

        token.consume(now);

        assertFalse(token.isUsableAt(now));
        assertEquals(now, token.getConsumedAt());
    }

    @Test
    void consumeIsIdempotentAndKeepsTheFirstTimestamp() {
        Instant now = Instant.now();
        OneTimeToken token = issue(now, now.plusSeconds(3600));

        token.consume(now);
        token.consume(now.plusSeconds(30));

        assertEquals(now, token.getConsumedAt());
    }

    private static OneTimeToken issue(Instant createdAt, Instant expiresAt) {
        return OneTimeToken.issue(
                "hash",
                OneTimeTokenPurpose.EMAIL_VERIFICATION,
                UUID.randomUUID(),
                createdAt,
                expiresAt
        );
    }
}
