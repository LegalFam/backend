package com.legalfam.backend.auth.infrastructure.adapter.out;

import com.legalfam.backend.auth.domain.model.RefreshToken;
import com.legalfam.backend.auth.infrastructure.persistence.entity.RefreshTokenEntity;

final class RefreshTokenEntityMapper {

    private RefreshTokenEntityMapper() {
    }

    static RefreshToken toDomain(RefreshTokenEntity entity) {
        return RefreshToken.restore(
                entity.getId(),
                entity.getToken(),
                entity.getExpiresAt(),
                entity.isRevoked(),
                entity.getUserId()
        );
    }

    static RefreshTokenEntity toEntity(RefreshToken domain) {
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setId(domain.getId());
        entity.setToken(domain.getToken());
        entity.setExpiresAt(domain.getExpiresAt());
        entity.setRevoked(domain.isRevoked());
        entity.setUserId(domain.getUserId());
        return entity;
    }
}
