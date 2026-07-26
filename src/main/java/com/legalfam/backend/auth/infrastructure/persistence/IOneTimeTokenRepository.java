package com.legalfam.backend.auth.infrastructure.persistence;

import com.legalfam.backend.auth.domain.model.OneTimeTokenPurpose;
import com.legalfam.backend.auth.infrastructure.persistence.entity.OneTimeTokenEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IOneTimeTokenRepository extends JpaRepository<OneTimeTokenEntity, UUID> {

    // Always filtered by purpose: a token minted for one flow must never unlock the other.
    Optional<OneTimeTokenEntity> findByTokenHashAndPurpose(String tokenHash, OneTimeTokenPurpose purpose);

    Optional<OneTimeTokenEntity> findFirstByUserIdAndPurposeOrderByCreatedAtDesc(
            UUID userId,
            OneTimeTokenPurpose purpose
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OneTimeTokenEntity t set t.consumedAt = :now "
            + "where t.userId = :userId and t.purpose = :purpose and t.consumedAt is null")
    int consumeAllByUserIdAndPurpose(
            @Param("userId") UUID userId,
            @Param("purpose") OneTimeTokenPurpose purpose,
            @Param("now") Instant now
    );
}
