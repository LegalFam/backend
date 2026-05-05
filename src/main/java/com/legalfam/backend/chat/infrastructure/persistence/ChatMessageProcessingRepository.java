package com.legalfam.backend.chat.infrastructure.persistence;

import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatMessageProcessingEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ChatMessageProcessingRepository extends JpaRepository<ChatMessageProcessingEntity, UUID> {

    Optional<ChatMessageProcessingEntity> findByUserMessageId(UUID userMessageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select processing from ChatMessageProcessingEntity processing where processing.userMessageId = :userMessageId")
    ChatMessageProcessingEntity findByUserMessageIdForUpdate(@Param("userMessageId") UUID userMessageId);
}
