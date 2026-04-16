package com.legalfam.backend.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final Key signingKey;
    private final long accessTokenExpirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.jwt.access-token-expiration-ms}") long accessTokenExpirationMs
    ) {
        this.signingKey = Keys.hmacShaKeyFor(normalizeSecret(jwtSecret));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
    }

    public String generateAccessToken(String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationMs / 1000;
    }

    private byte[] normalizeSecret(String secret) {
        String normalized = secret == null ? "" : secret.trim();
        if (normalized.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters long");
        }
        return normalized.getBytes(StandardCharsets.UTF_8);
    }
}
