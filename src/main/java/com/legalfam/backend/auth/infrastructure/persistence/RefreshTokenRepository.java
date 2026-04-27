package com.legalfam.backend.auth.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import com.legalfam.backend.auth.domain.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);
}
