package com.legalfam.backend.chat.application.port.out;

import com.legalfam.backend.chat.domain.model.ChatCitation;
import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessing;
import com.legalfam.backend.chat.domain.model.ChatOutboxEvent;
import com.legalfam.backend.chat.domain.model.ChatOutboxEventStatus;
import com.legalfam.backend.chat.domain.model.ChatSession;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IChatPersistencePort {
    Optional<ChatSession> findSessionById(UUID sessionId);

    ChatSession saveSession(ChatSession chatSession);

    void deleteSessionById(UUID sessionId);

    List<ChatSession> findSessionsByUserIdOrderByUpdatedAtDesc(UUID userId);

    ChatMessage saveMessage(ChatMessage chatMessage);

    Optional<ChatMessage> findMessageById(UUID messageId);

    List<ChatMessage> findMessagesBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    List<ChatCitation> findCitationsByMessageIdsOrderByMessageIdAndId(List<UUID> messageIds);

    ChatCitation saveCitation(ChatCitation chatCitation);

    ChatMessageProcessing saveMessageProcessing(ChatMessageProcessing chatMessageProcessing);

    Optional<ChatMessageProcessing> findMessageProcessingByUserMessageId(UUID userMessageId);

    Optional<ChatMessageProcessing> findMessageProcessingByUserMessageIdForUpdate(UUID userMessageId);

    ChatOutboxEvent saveOutboxEvent(ChatOutboxEvent chatOutboxEvent);

    Optional<ChatOutboxEvent> findOutboxEventByAggregateId(UUID aggregateId);

    Optional<ChatOutboxEvent> findOutboxEventByAggregateIdForUpdate(UUID aggregateId);

    List<ChatOutboxEvent> findOutboxEventsByAggregateIds(List<UUID> aggregateIds);

    boolean existsUnreadAssistantMessageBySessionId(UUID sessionId);

    List<ChatOutboxEvent> lockReadyOutboxEvents(Instant now, int batchSize);

    long deleteOutboxEventsByStatusAndReadAtBefore(ChatOutboxEventStatus status, Instant threshold);

    long deleteOutboxEventsByStatusInAndUpdatedAtBefore(Collection<ChatOutboxEventStatus> statuses, Instant threshold);
}
