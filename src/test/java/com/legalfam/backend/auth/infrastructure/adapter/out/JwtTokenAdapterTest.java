package com.legalfam.backend.auth.infrastructure.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.legalfam.backend.auth.infrastructure.config.JwtProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtTokenAdapterTest {

    @Test
    void constructorThrowsWhenSecretIsTooShort() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtTokenAdapter(jwtProperties("short-secret", 900_000L))
        );
    }

    @Test
    void generatedTokenIsValidAndContainsEmail() {
        JwtTokenAdapter jwtTokenAdapter = new JwtTokenAdapter(jwtProperties("12345678901234567890123456789012", 900_000L));
        UUID userId = UUID.randomUUID();

        String token = jwtTokenAdapter.generateAccessToken(userId, "user@example.com");

        assertTrue(jwtTokenAdapter.isTokenValid(token));
        assertEquals("user@example.com", jwtTokenAdapter.extractEmail(token));
        assertEquals(userId, jwtTokenAdapter.extractUserId(token));
        assertEquals(900L, jwtTokenAdapter.getAccessTokenExpirationSeconds());
    }

    @Test
    void randomStringIsNotAValidToken() {
        JwtTokenAdapter jwtTokenAdapter = new JwtTokenAdapter(jwtProperties("12345678901234567890123456789012", 900_000L));

        assertFalse(jwtTokenAdapter.isTokenValid("not-a-jwt"));
    }

    private JwtProperties jwtProperties(String secret, long accessTokenExpirationMs) {
        return new JwtProperties(secret, accessTokenExpirationMs, 604_800_000L);
    }
}
