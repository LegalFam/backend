package com.legalfam.backend.chat.infrastructure.persistence;

import com.legalfam.backend.chat.domain.model.ChatMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findByChatSessionIdOrderByCreatedAtAsc(UUID chatSessionId);
}

