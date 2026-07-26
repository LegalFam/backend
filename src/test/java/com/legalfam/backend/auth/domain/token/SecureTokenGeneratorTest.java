package com.legalfam.backend.auth.domain.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecureTokenGeneratorTest {

    @Test
    void generatedTokensAreUrlSafeAndUnpadded() {
        String token = SecureTokenGenerator.generateRawToken(SecureTokenGenerator.ONE_TIME_TOKEN_BYTES);

        assertTrue(token.matches("[A-Za-z0-9_-]+"), "token must be url-safe base64: " + token);
        // 32 bytes -> 43 base64 characters without padding.
        assertEquals(43, token.length());
    }

    @Test
    void generatedTokensDoNotRepeat() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            tokens.add(SecureTokenGenerator.generateRawToken(SecureTokenGenerator.ONE_TIME_TOKEN_BYTES));
        }

        assertEquals(500, tokens.size());
    }

    @Test
    void hashIsDeterministicAndDiffersFromTheRawToken() {
        String raw = SecureTokenGenerator.generateRawToken(SecureTokenGenerator.ONE_TIME_TOKEN_BYTES);

        assertEquals(SecureTokenGenerator.hash(raw), SecureTokenGenerator.hash(raw));
        assertNotEquals(raw, SecureTokenGenerator.hash(raw));
        assertNotEquals(
                SecureTokenGenerator.hash(raw),
                SecureTokenGenerator.hash(raw + "x")
        );
    }
}
