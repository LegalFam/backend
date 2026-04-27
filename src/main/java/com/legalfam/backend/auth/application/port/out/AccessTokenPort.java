package com.legalfam.backend.auth.application.port.out;

import java.util.UUID;

public interface AccessTokenPort {
    String generateAccessToken(UUID userId, String email);

    long getAccessTokenExpirationSeconds();
}
