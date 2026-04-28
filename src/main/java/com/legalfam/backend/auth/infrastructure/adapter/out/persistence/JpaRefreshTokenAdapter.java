package com.legalfam.backend.auth.infrastructure.adapter.out.persistence;

import com.legalfam.backend.auth.application.port.out.RefreshTokenPort;
import com.legalfam.backend.auth.domain.model.RefreshToken;
import com.legalfam.backend.auth.infrastructure.persistence.RefreshTokenRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JpaRefreshTokenAdapter implements RefreshTokenPort {

    private final RefreshTokenRepository refreshTokenRepository;

    public JpaRefreshTokenAdapter(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Override
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token).map(RefreshTokenEntityMapper::toDomain);
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        return RefreshTokenEntityMapper.toDomain(
                refreshTokenRepository.save(RefreshTokenEntityMapper.toEntity(refreshToken))
        );
    }
}
