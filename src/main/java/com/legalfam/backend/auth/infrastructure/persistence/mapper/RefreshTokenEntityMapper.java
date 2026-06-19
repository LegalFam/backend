package com.legalfam.backend.auth.infrastructure.persistence.mapper;

import com.legalfam.backend.auth.domain.model.RefreshToken;
import com.legalfam.backend.auth.infrastructure.persistence.entity.RefreshTokenEntity;

public final class RefreshTokenEntityMapper {

    private RefreshTokenEntityMapper() {
    }

    public static RefreshToken toDomain(RefreshTokenEntity entity) {
        return RefreshToken.restore(
                entity.getId(),
                entity.getToken(),
                entity.getExpiresAt(),
                entity.isRevoked(),
                entity.getUserId()
        );
    }

    public static RefreshTokenEntity toEntity(RefreshToken domain) {
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setId(domain.getId());
        entity.setToken(domain.getToken());
        entity.setExpiresAt(domain.getExpiresAt());
        entity.setRevoked(domain.isRevoked());
        entity.setUserId(domain.getUserId());
        return entity;
    }
}
