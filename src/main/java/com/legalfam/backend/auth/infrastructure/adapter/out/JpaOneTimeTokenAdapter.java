package com.legalfam.backend.auth.infrastructure.adapter.out;

import com.legalfam.backend.auth.application.port.out.IOneTimeTokenPersistencePort;
import com.legalfam.backend.auth.domain.model.OneTimeToken;
import com.legalfam.backend.auth.domain.model.OneTimeTokenPurpose;
import com.legalfam.backend.auth.infrastructure.persistence.IOneTimeTokenRepository;
import com.legalfam.backend.auth.infrastructure.persistence.entity.OneTimeTokenEntity;
import com.legalfam.backend.auth.infrastructure.persistence.mapper.OneTimeTokenEntityMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaOneTimeTokenAdapter implements IOneTimeTokenPersistencePort {

    private final IOneTimeTokenRepository IOneTimeTokenRepository;

    public JpaOneTimeTokenAdapter(IOneTimeTokenRepository IOneTimeTokenRepository) {
        this.IOneTimeTokenRepository = IOneTimeTokenRepository;
    }

    @Override
    public OneTimeToken save(OneTimeToken token) {
        return OneTimeTokenEntityMapper.toDomain(
                IOneTimeTokenRepository.save(OneTimeTokenEntityMapper.toEntity(token))
        );
    }

    @Override
    public Optional<OneTimeToken> findByHashAndPurpose(String tokenHash, OneTimeTokenPurpose purpose) {
        return IOneTimeTokenRepository.findByTokenHashAndPurpose(tokenHash, purpose)
                .map(OneTimeTokenEntityMapper::toDomain);
    }

    @Override
    public Optional<Instant> findLatestIssuedAt(UUID userId, OneTimeTokenPurpose purpose) {
        return IOneTimeTokenRepository.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(userId, purpose)
                .map(OneTimeTokenEntity::getCreatedAt);
    }

    @Override
    public void consumeAllFor(UUID userId, OneTimeTokenPurpose purpose, Instant consumedAt) {
        IOneTimeTokenRepository.consumeAllByUserIdAndPurpose(userId, purpose, consumedAt);
    }
}
