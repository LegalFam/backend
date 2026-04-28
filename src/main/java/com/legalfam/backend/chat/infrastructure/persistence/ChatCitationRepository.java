package com.legalfam.backend.chat.infrastructure.persistence;

import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatCitationEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatCitationRepository extends JpaRepository<ChatCitationEntity, UUID> {
    List<ChatCitationEntity> findByChatMessageIdOrderByIdAsc(UUID chatMessageId);

    List<ChatCitationEntity> findByChatMessageIdInOrderByChatMessageIdAscIdAsc(List<UUID> chatMessageIds);
}

