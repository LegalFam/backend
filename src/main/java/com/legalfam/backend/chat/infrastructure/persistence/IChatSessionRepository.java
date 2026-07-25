package com.legalfam.backend.chat.infrastructure.persistence;

import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatSessionEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IChatSessionRepository extends JpaRepository<ChatSessionEntity, UUID> {
    Slice<ChatSessionEntity> findByUserIdOrderByUpdatedAtDesc(UUID userId, Pageable pageable);

    Slice<ChatSessionEntity> findByUserIdAndUpdatedAtAfterOrderByUpdatedAtDesc(
            UUID userId,
            Instant threshold,
            Pageable pageable
    );
}

