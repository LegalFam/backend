package com.legalfam.backend.auth.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void newUserStartsUnverified() {
        User user = User.create("user@example.com", "hashed", "Juan", "900000000");

        assertFalse(user.isEmailVerified());
        assertNull(user.getEmailVerifiedAt());
    }

    @Test
    void verifyEmailMarksUserAndStampsTheMoment() {
        User user = User.create("user@example.com", "hashed", "Juan", "900000000");
        Instant now = Instant.now();

        user.verifyEmail(now);

        assertTrue(user.isEmailVerified());
        assertEquals(now, user.getEmailVerifiedAt());
    }

    @Test
    void verifyEmailIsIdempotentAndKeepsTheOriginalTimestamp() {
        User user = User.create("user@example.com", "hashed", "Juan", "900000000");
        Instant first = Instant.now();

        user.verifyEmail(first);
        user.verifyEmail(first.plusSeconds(3600));

        assertEquals(first, user.getEmailVerifiedAt());
    }
}
