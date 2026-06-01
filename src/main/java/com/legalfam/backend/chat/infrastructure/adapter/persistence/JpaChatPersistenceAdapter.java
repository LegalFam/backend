package com.legalfam.backend.chat.infrastructure.adapter.persistence;

import com.legalfam.backend.chat.application.port.out.ChatPersistencePort;
import com.legalfam.backend.chat.domain.model.ChatCitation;
import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessing;
import com.legalfam.backend.chat.domain.model.ChatOutboxEvent;
import com.legalfam.backend.chat.domain.model.ChatOutboxEventStatus;
import com.legalfam.backend.chat.domain.model.ChatSession;
import com.legalfam.backend.chat.infrastructure.persistence.ChatCitationRepository;
import com.legalfam.backend.chat.infrastructure.persistence.ChatMessageRepository;
import com.legalfam.backend.chat.infrastructure.persistence.ChatMessageProcessingRepository;
import com.legalfam.backend.chat.infrastructure.persistence.ChatOutboxEventRepository;
import com.legalfam.backend.chat.infrastructure.persistence.ChatSessionRepository;
import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatMessageEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaChatPersistenceAdapter implements ChatPersistencePort {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatCitationRepository chatCitationRepository;
    private final ChatMessageProcessingRepository chatMessageProcessingRepository;
    private final ChatOutboxEventRepository chatOutboxEventRepository;

    public JpaChatPersistenceAdapter(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            ChatCitationRepository chatCitationRepository,
            ChatMessageProcessingRepository chatMessageProcessingRepository,
            ChatOutboxEventRepository chatOutboxEventRepository
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatCitationRepository = chatCitationRepository;
        this.chatMessageProcessingRepository = chatMessageProcessingRepository;
        this.chatOutboxEventRepository = chatOutboxEventRepository;
    }

    @Override
    public Optional<ChatSession> findSessionById(UUID sessionId) {
        return chatSessionRepository.findById(sessionId).map(ChatEntityMapper::toDomain);
    }

    @Override
    public ChatSession saveSession(ChatSession chatSession) {
        return ChatEntityMapper.toDomain(chatSessionRepository.save(ChatEntityMapper.toEntity(chatSession)));
    }

    @Override
    public void deleteSessionById(UUID sessionId) {
        List<UUID> messageIds = chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(ChatMessageEntity::getId)
                .toList();
        if (!messageIds.isEmpty()) {
            chatCitationRepository.deleteByChatMessageIdIn(messageIds);
            chatMessageProcessingRepository.deleteByUserMessageIdIn(messageIds);
        }
        chatOutboxEventRepository.deleteByChatSessionId(sessionId);
        chatMessageRepository.deleteByChatSessionId(sessionId);
        chatSessionRepository.deleteById(sessionId);
    }

    @Override
    public List<ChatSession> findSessionsByUserIdOrderByUpdatedAtDesc(UUID userId) {
        return chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(ChatEntityMapper::toDomain)
                .toList();
    }

    @Override
    public ChatMessage saveMessage(ChatMessage chatMessage) {
        return ChatEntityMapper.toDomain(chatMessageRepository.save(ChatEntityMapper.toEntity(chatMessage)));
    }

    @Override
    public Optional<ChatMessage> findMessageById(UUID messageId) {
        return chatMessageRepository.findById(messageId).map(ChatEntityMapper::toDomain);
    }

    @Override
    public List<ChatMessage> findMessagesBySessionIdOrderByCreatedAtAsc(UUID sessionId) {
        return chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(ChatEntityMapper::toDomain)
                .toList();
    }

    @Override
    public List<ChatCitation> findCitationsByMessageIdsOrderByMessageIdAndId(List<UUID> messageIds) {
        return chatCitationRepository.findByChatMessageIdInOrderByChatMessageIdAscIdAsc(messageIds)
                .stream()
                .map(ChatEntityMapper::toDomain)
                .toList();
    }

    @Override
    public ChatCitation saveCitation(ChatCitation chatCitation) {
        return ChatEntityMapper.toDomain(chatCitationRepository.save(ChatEntityMapper.toEntity(chatCitation)));
    }

    @Override
    public ChatMessageProcessing saveMessageProcessing(ChatMessageProcessing chatMessageProcessing) {
        return ChatEntityMapper.toDomain(
                chatMessageProcessingRepository.save(ChatEntityMapper.toEntity(chatMessageProcessing))
        );
    }

    @Override
    public Optional<ChatMessageProcessing> findMessageProcessingByUserMessageId(UUID userMessageId) {
        return chatMessageProcessingRepository.findByUserMessageId(userMessageId).map(ChatEntityMapper::toDomain);
    }

    @Override
    public Optional<ChatMessageProcessing> findMessageProcessingByUserMessageIdForUpdate(UUID userMessageId) {
        return Optional.ofNullable(chatMessageProcessingRepository.findByUserMessageIdForUpdate(userMessageId))
                .map(ChatEntityMapper::toDomain);
    }

    @Override
    public ChatOutboxEvent saveOutboxEvent(ChatOutboxEvent chatOutboxEvent) {
        return ChatEntityMapper.toDomain(
                chatOutboxEventRepository.save(ChatEntityMapper.toEntity(chatOutboxEvent))
        );
    }

    @Override
    public Optional<ChatOutboxEvent> findOutboxEventByAggregateId(UUID aggregateId) {
        return chatOutboxEventRepository.findByAggregateId(aggregateId).map(ChatEntityMapper::toDomain);
    }

    @Override
    public Optional<ChatOutboxEvent> findOutboxEventByAggregateIdForUpdate(UUID aggregateId) {
        return Optional.ofNullable(chatOutboxEventRepository.findByAggregateIdForUpdate(aggregateId))
                .map(ChatEntityMapper::toDomain);
    }

    @Override
    public List<ChatOutboxEvent> findOutboxEventsByAggregateIds(List<UUID> aggregateIds) {
        if (aggregateIds.isEmpty()) {
            return List.of();
        }
        return chatOutboxEventRepository.findByAggregateIdIn(aggregateIds)
                .stream()
                .map(ChatEntityMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsUnreadAssistantMessageBySessionId(UUID sessionId) {
        return chatOutboxEventRepository.existsUnreadAssistantMessageBySessionId(sessionId);
    }

    @Override
    public List<ChatOutboxEvent> lockReadyOutboxEvents(Instant now, int batchSize) {
        return chatOutboxEventRepository.lockReadyBatch(now, batchSize)
                .stream()
                .map(ChatEntityMapper::toDomain)
                .toList();
    }

    @Override
    public long deleteOutboxEventsByStatusAndReadAtBefore(ChatOutboxEventStatus status, Instant threshold) {
        return chatOutboxEventRepository.deleteByStatusAndReadAtBefore(status, threshold);
    }

    @Override
    public long deleteOutboxEventsByStatusInAndUpdatedAtBefore(
            Collection<ChatOutboxEventStatus> statuses,
            Instant threshold
    ) {
        return chatOutboxEventRepository.deleteByStatusInAndUpdatedAtBefore(statuses, threshold);
    }
}
