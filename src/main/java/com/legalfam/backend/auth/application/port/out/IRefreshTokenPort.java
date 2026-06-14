package com.legalfam.backend.auth.application.port.out;

import com.legalfam.backend.auth.domain.model.RefreshToken;
import java.util.Optional;

public interface IRefreshTokenPort {
    Optional<RefreshToken> findByToken(String token);

    RefreshToken save(RefreshToken refreshToken);
}
