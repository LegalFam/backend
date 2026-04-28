package com.legalfam.backend.auth.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import com.legalfam.backend.auth.infrastructure.persistence.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
    Optional<RefreshTokenEntity> findByToken(String token);
}
