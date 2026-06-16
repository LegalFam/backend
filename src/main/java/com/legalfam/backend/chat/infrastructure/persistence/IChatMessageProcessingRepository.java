package com.legalfam.backend.chat.infrastructure.persistence;

import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatMessageProcessingEntity;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessingStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface IChatMessageProcessingRepository extends JpaRepository<ChatMessageProcessingEntity, UUID> {

    Optional<ChatMessageProcessingEntity> findByUserMessageId(UUID userMessageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select processing from ChatMessageProcessingEntity processing where processing.userMessageId = :userMessageId")
    ChatMessageProcessingEntity findByUserMessageIdForUpdate(@Param("userMessageId") UUID userMessageId);

    @Query("""
            select processing
            from ChatMessageProcessingEntity processing
            join ChatMessageEntity message on message.id = processing.userMessageId
            join ChatSessionEntity session on session.id = message.chatSessionId
            where session.userId = :userId
              and processing.status in :statuses
            order by processing.updatedAt desc
            """)
    List<ChatMessageProcessingEntity> findActiveByUserId(
            @Param("userId") UUID userId,
            @Param("statuses") Collection<ChatMessageProcessingStatus> statuses,
            Pageable pageable
    );

    long deleteByUserMessageIdIn(Collection<UUID> userMessageIds);
}
