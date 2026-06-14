package com.legalfam.backend.auth.infrastructure.adapter.out;

import com.legalfam.backend.auth.domain.model.RefreshToken;
import com.legalfam.backend.auth.infrastructure.persistence.entity.RefreshTokenEntity;

final class RefreshTokenEntityMapper {

    private RefreshTokenEntityMapper() {
    }

    static RefreshToken toDomain(RefreshTokenEntity entity) {
        RefreshToken token = new RefreshToken();
        token.setId(entity.getId());
        token.setToken(entity.getToken());
        token.setExpiresAt(entity.getExpiresAt());
        token.setRevoked(entity.isRevoked());
        token.setUserId(entity.getUserId());
        return token;
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
