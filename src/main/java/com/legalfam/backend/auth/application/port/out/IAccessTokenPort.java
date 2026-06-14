package com.legalfam.backend.auth.application.port.out;

import java.util.UUID;

public interface IAccessTokenPort {
    String generateAccessToken(UUID userId, String email);

    long getAccessTokenExpirationSeconds();
}
