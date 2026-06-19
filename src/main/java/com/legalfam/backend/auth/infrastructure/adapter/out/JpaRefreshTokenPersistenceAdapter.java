package com.legalfam.backend.auth.infrastructure.adapter.out;

import com.legalfam.backend.auth.application.port.out.IRefreshTokenPersistencePort;
import com.legalfam.backend.auth.domain.model.RefreshToken;
import com.legalfam.backend.auth.infrastructure.persistence.IRefreshTokenRepository;
import com.legalfam.backend.auth.infrastructure.persistence.mapper.RefreshTokenEntityMapper;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JpaRefreshTokenPersistenceAdapter implements IRefreshTokenPersistencePort {

    private final IRefreshTokenRepository IRefreshTokenRepository;

    public JpaRefreshTokenPersistenceAdapter(IRefreshTokenRepository IRefreshTokenRepository) {
        this.IRefreshTokenRepository = IRefreshTokenRepository;
    }

    @Override
    public Optional<RefreshToken> findByToken(String token) {
        return IRefreshTokenRepository.findByToken(token).map(RefreshTokenEntityMapper::toDomain);
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        return RefreshTokenEntityMapper.toDomain(
                IRefreshTokenRepository.save(RefreshTokenEntityMapper.toEntity(refreshToken))
        );
    }
}
