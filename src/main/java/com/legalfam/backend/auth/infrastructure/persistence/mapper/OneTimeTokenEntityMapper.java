package com.legalfam.backend.auth.infrastructure.persistence.mapper;

import com.legalfam.backend.auth.domain.model.OneTimeToken;
import com.legalfam.backend.auth.infrastructure.persistence.entity.OneTimeTokenEntity;

public final class OneTimeTokenEntityMapper {

    private OneTimeTokenEntityMapper() {
    }

    public static OneTimeToken toDomain(OneTimeTokenEntity entity) {
        return OneTimeToken.restore(
                entity.getId(),
                entity.getTokenHash(),
                entity.getPurpose(),
                entity.getUserId(),
                entity.getCreatedAt(),
                entity.getExpiresAt(),
                entity.getConsumedAt()
        );
    }

    public static OneTimeTokenEntity toEntity(OneTimeToken domain) {
        OneTimeTokenEntity entity = new OneTimeTokenEntity();
        entity.setId(domain.getId());
        entity.setTokenHash(domain.getTokenHash());
        entity.setPurpose(domain.getPurpose());
        entity.setUserId(domain.getUserId());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setExpiresAt(domain.getExpiresAt());
        entity.setConsumedAt(domain.getConsumedAt());
        return entity;
    }
}
