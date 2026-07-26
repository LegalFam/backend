package com.legalfam.backend.auth.application.port.out;

import com.legalfam.backend.auth.domain.model.RefreshToken;
import java.util.Optional;
import java.util.UUID;

public interface IRefreshTokenPersistencePort {
    Optional<RefreshToken> findByToken(String token);

    RefreshToken save(RefreshToken refreshToken);

    void revokeAllForUser(UUID userId);
}
