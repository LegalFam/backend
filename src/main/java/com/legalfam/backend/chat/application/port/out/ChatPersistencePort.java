package com.legalfam.backend.chat.application.port.out;

import com.legalfam.backend.chat.domain.model.ChatCitation;
import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatPersistencePort {
    Optional<ChatSession> findSessionById(UUID sessionId);

    ChatSession saveSession(ChatSession chatSession);

    List<ChatSession> findSessionsByUserIdOrderByUpdatedAtDesc(UUID userId);

    ChatMessage saveMessage(ChatMessage chatMessage);

    Optional<ChatMessage> findMessageById(UUID messageId);

    List<ChatMessage> findMessagesBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    List<ChatCitation> findCitationsByMessageIdsOrderByMessageIdAndId(List<UUID> messageIds);

    ChatCitation saveCitation(ChatCitation chatCitation);
}
