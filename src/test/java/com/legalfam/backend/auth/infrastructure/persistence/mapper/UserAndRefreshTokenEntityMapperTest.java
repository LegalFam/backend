package com.legalfam.backend.auth.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.legalfam.backend.auth.infrastructure.persistence.entity.RefreshTokenEntity;
import com.legalfam.backend.auth.infrastructure.persistence.entity.UserEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserAndRefreshTokenEntityMapperTest {

    @Test
    void roundTripsUser() {
        UserEntity entity = new UserEntity();
        entity.setId(UUID.randomUUID());
        entity.setEmail("ana@legalfam.test");
        entity.setPassword("hash");
        entity.setName("Ana");
        entity.setPhone("999888777");
        entity.setEmailVerified(true);
        entity.setEmailVerifiedAt(Instant.parse("2026-11-15T10:00:00Z"));

        assertThat(UserEntityMapper.toEntity(UserEntityMapper.toDomain(entity)))
                .usingRecursiveComparison()
                .isEqualTo(entity);
    }

    @Test
    void roundTripsRefreshToken() {
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setId(UUID.randomUUID());
        entity.setToken("refresh");
        entity.setExpiresAt(Instant.parse("2026-12-15T10:00:00Z"));
        entity.setRevoked(true);
        entity.setUserId(UUID.randomUUID());

        assertThat(RefreshTokenEntityMapper.toEntity(RefreshTokenEntityMapper.toDomain(entity)))
                .usingRecursiveComparison()
                .isEqualTo(entity);
    }
}
