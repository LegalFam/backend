package com.legalfam.backend.auth.infrastructure.persistence.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.legalfam.backend.auth.domain.model.OneTimeToken;
import com.legalfam.backend.auth.domain.model.OneTimeTokenPurpose;
import com.legalfam.backend.auth.infrastructure.persistence.entity.OneTimeTokenEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OneTimeTokenEntityMapperTest {

    @Test
    void roundTripsAnUnconsumedToken() {
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Instant expiresAt = createdAt.plusSeconds(3600);
        OneTimeToken domain = OneTimeToken.issue(
                "token-hash",
                OneTimeTokenPurpose.PASSWORD_RESET,
                userId,
                createdAt,
                expiresAt
        );

        OneTimeToken result = OneTimeTokenEntityMapper.toDomain(OneTimeTokenEntityMapper.toEntity(domain));

        assertEquals("token-hash", result.getTokenHash());
        assertEquals(OneTimeTokenPurpose.PASSWORD_RESET, result.getPurpose());
        assertEquals(userId, result.getUserId());
        assertEquals(createdAt, result.getCreatedAt());
        assertEquals(expiresAt, result.getExpiresAt());
        assertNull(result.getConsumedAt());
    }

    @Test
    void carriesIdAndConsumedAt() {
        UUID id = UUID.randomUUID();
        Instant consumedAt = Instant.now();
        OneTimeTokenEntity entity = new OneTimeTokenEntity();
        entity.setId(id);
        entity.setTokenHash("token-hash");
        entity.setPurpose(OneTimeTokenPurpose.EMAIL_VERIFICATION);
        entity.setUserId(UUID.randomUUID());
        entity.setCreatedAt(consumedAt.minusSeconds(60));
        entity.setExpiresAt(consumedAt.plusSeconds(3600));
        entity.setConsumedAt(consumedAt);

        OneTimeToken domain = OneTimeTokenEntityMapper.toDomain(entity);

        assertEquals(id, domain.getId());
        assertEquals(consumedAt, domain.getConsumedAt());
        assertEquals(id, OneTimeTokenEntityMapper.toEntity(domain).getId());
    }
}
