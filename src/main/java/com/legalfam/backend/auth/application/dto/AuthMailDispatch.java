package com.legalfam.backend.auth.application.dto;

/**
 * Carries a freshly minted one-time token from the transactional use case to the
 * mail dispatcher. The raw token lives only in memory: it is never serialized,
 * persisted or logged.
 */
public record AuthMailDispatch(
        String email,
        String name,
        String rawToken,
        long expiresInMinutes
) {
}
