package com.legalfam.backend.chat.infrastructure.persistence;

import com.legalfam.backend.chat.domain.model.ChatCitation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatCitationRepository extends JpaRepository<ChatCitation, UUID> {
    List<ChatCitation> findByChatMessageIdOrderByIdAsc(UUID chatMessageId);

    List<ChatCitation> findByChatMessageIdInOrderByChatMessageIdAscIdAsc(List<UUID> chatMessageIds);
}

