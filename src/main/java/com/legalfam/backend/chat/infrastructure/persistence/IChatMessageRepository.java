package com.legalfam.backend.chat.infrastructure.persistence;

import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatMessageEntity;
import com.legalfam.backend.chat.domain.model.ChatMessageRole;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {
    List<ChatMessageEntity> findByChatSessionIdOrderByCreatedAtAsc(UUID chatSessionId);

    Slice<ChatMessageEntity> findByChatSessionIdOrderByCreatedAtAsc(UUID chatSessionId, Pageable pageable);

    Slice<ChatMessageEntity> findByChatSessionIdOrderByCreatedAtDesc(UUID chatSessionId, Pageable pageable);

    Slice<ChatMessageEntity> findByChatSessionIdAndRoleInOrderByCreatedAtDesc(
            UUID chatSessionId,
            List<ChatMessageRole> roles,
            Pageable pageable
    );

    long deleteByChatSessionId(UUID chatSessionId);
}

