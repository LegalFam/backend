package com.legalfam.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    @Test
    void constructorThrowsWhenSecretIsTooShort() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtService("short-secret", 900_000L)
        );
    }

    @Test
    void generatedTokenIsValidAndContainsEmail() {
        JwtService jwtService = new JwtService("12345678901234567890123456789012", 900_000L);
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(userId, "user@example.com");

        assertTrue(jwtService.isTokenValid(token));
        assertEquals("user@example.com", jwtService.extractEmail(token));
        assertEquals(userId, jwtService.extractUserId(token));
        assertEquals(900L, jwtService.getAccessTokenExpirationSeconds());
    }

    @Test
    void randomStringIsNotAValidToken() {
        JwtService jwtService = new JwtService("12345678901234567890123456789012", 900_000L);

        assertFalse(jwtService.isTokenValid("not-a-jwt"));
    }
}
