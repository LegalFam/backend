package com.legalfam.backend.chat.infrastructure.adapter.out;

import com.legalfam.backend.chat.application.port.out.IChatPersistencePort;
import com.legalfam.backend.chat.domain.model.ChatCitation;
import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessing;
import com.legalfam.backend.chat.domain.model.ChatOutboxEvent;
import com.legalfam.backend.chat.domain.model.ChatOutboxEventStatus;
import com.legalfam.backend.chat.domain.model.ChatMessageRole;
import com.legalfam.backend.chat.domain.model.ChatSession;
import com.legalfam.backend.chat.infrastructure.persistence.IChatCitationRepository;
import com.legalfam.backend.chat.infrastructure.persistence.IChatMessageRepository;
import com.legalfam.backend.chat.infrastructure.persistence.IChatMessageProcessingRepository;
import com.legalfam.backend.chat.infrastructure.persistence.IChatOutboxEventRepository;
import com.legalfam.backend.chat.infrastructure.persistence.IChatSessionRepository;
import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatMessageEntity;
import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatSessionEntity;
import com.legalfam.backend.common.cursor.CursorQuery;
import com.legalfam.backend.common.cursor.CursorResult;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class JpaChatPersistenceAdapter implements IChatPersistencePort {

    private final IChatSessionRepository IChatSessionRepository;
    private final IChatMessageRepository IChatMessageRepository;
    private final IChatCitationRepository IChatCitationRepository;
    private final IChatMessageProcessingRepository IChatMessageProcessingRepository;
    private final IChatOutboxEventRepository IChatOutboxEventRepository;

    public JpaChatPersistenceAdapter(
            IChatSessionRepository IChatSessionRepository,
            IChatMessageRepository IChatMessageRepository,
            IChatCitationRepository IChatCitationRepository,
            IChatMessageProcessingRepository IChatMessageProcessingRepository,
            IChatOutboxEventRepository IChatOutboxEventRepository
    ) {
        this.IChatSessionRepository = IChatSessionRepository;
        this.IChatMessageRepository = IChatMessageRepository;
        this.IChatCitationRepository = IChatCitationRepository;
        this.IChatMessageProcessingRepository = IChatMessageProcessingRepository;
        this.IChatOutboxEventRepository = IChatOutboxEventRepository;
    }

    @Override
    public Optional<ChatSession> findSessionById(UUID sessionId) {
        return IChatSessionRepository.findById(sessionId).map(ChatEntityMapper::toDomain);
    }

    @Override
    public ChatSession saveSession(ChatSession chatSession) {
        return ChatEntityMapper.toDomain(IChatSessionRepository.save(ChatEntityMapper.toEntity(chatSession)));
    }

    @Override
    public void deleteSessionById(UUID sessionId) {
        List<UUID> messageIds = IChatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(ChatMessageEntity::getId)
                .toList();
        if (!messageIds.isEmpty()) {
            IChatCitationRepository.deleteByChatMessageIdIn(messageIds);
            IChatMessageProcessingRepository.deleteByUserMessageIdIn(messageIds);
        }
        IChatOutboxEventRepository.deleteByChatSessionId(sessionId);
        IChatMessageRepository.deleteByChatSessionId(sessionId);
        IChatSessionRepository.deleteById(sessionId);
    }

    @Override
    public CursorResult<ChatSession> findSessionsByUserIdOrderByUpdatedAtDesc(UUID userId, CursorQuery cursorQuery) {
        Slice<ChatSessionEntity> page = IChatSessionRepository.findByUserIdOrderByUpdatedAtDesc(
                userId,
                OffsetPageRequest.of(cursorQuery.offset(), cursorQuery.size())
        );
        return toCursorResult(page, cursorQuery, ChatEntityMapper::toDomain);
    }

    @Override
    public ChatMessage saveMessage(ChatMessage chatMessage) {
        return ChatEntityMapper.toDomain(IChatMessageRepository.save(ChatEntityMapper.toEntity(chatMessage)));
    }

    @Override
    public Optional<ChatMessage> findMessageById(UUID messageId) {
        return IChatMessageRepository.findById(messageId).map(ChatEntityMapper::toDomain);
    }

    @Override
    public CursorResult<ChatMessage> findMessagesBySessionIdOrderByCreatedAtDesc(UUID sessionId, CursorQuery cursorQuery) {
        Slice<ChatMessageEntity> page = IChatMessageRepository.findByChatSessionIdOrderByCreatedAtDesc(
                sessionId,
                OffsetPageRequest.of(cursorQuery.offset(), cursorQuery.size())
        );
        return toCursorResult(page, cursorQuery, ChatEntityMapper::toDomain);
    }

    @Override
    public List<ChatMessage> findRecentMessagesForAssistantContext(UUID sessionId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        Slice<ChatMessageEntity> page = IChatMessageRepository.findByChatSessionIdAndRoleInOrderByCreatedAtDesc(
                sessionId,
                List.of(ChatMessageRole.USER, ChatMessageRole.ASSISTANT),
                OffsetPageRequest.of(0, limit)
        );
        List<ChatMessage> messages = page.getContent().stream()
                .map(ChatEntityMapper::toDomain)
                .toList();
        Collections.reverse(messages);
        return messages;
    }

    @Override
    public List<ChatCitation> findCitationsByMessageIdsOrderByMessageIdAndId(List<UUID> messageIds) {
        return IChatCitationRepository.findByChatMessageIdInOrderByChatMessageIdAscIdAsc(messageIds)
                .stream()
                .map(ChatEntityMapper::toDomain)
                .toList();
    }

    @Override
    public ChatCitation saveCitation(ChatCitation chatCitation) {
        return ChatEntityMapper.toDomain(IChatCitationRepository.save(ChatEntityMapper.toEntity(chatCitation)));
    }

    @Override
    public ChatMessageProcessing saveMessageProcessing(ChatMessageProcessing chatMessageProcessing) {
        return ChatEntityMapper.toDomain(
                IChatMessageProcessingRepository.save(ChatEntityMapper.toEntity(chatMessageProcessing))
        );
    }

    @Override
    public Optional<ChatMessageProcessing> findMessageProcessingByUserMessageId(UUID userMessageId) {
        return IChatMessageProcessingRepository.findByUserMessageId(userMessageId).map(ChatEntityMapper::toDomain);
    }

    @Override
    public Optional<ChatMessageProcessing> findMessageProcessingByUserMessageIdForUpdate(UUID userMessageId) {
        return Optional.ofNullable(IChatMessageProcessingRepository.findByUserMessageIdForUpdate(userMessageId))
                .map(ChatEntityMapper::toDomain);
    }

    @Override
    public ChatOutboxEvent saveOutboxEvent(ChatOutboxEvent chatOutboxEvent) {
        return ChatEntityMapper.toDomain(
                IChatOutboxEventRepository.save(ChatEntityMapper.toEntity(chatOutboxEvent))
        );
    }

    @Override
    public Optional<ChatOutboxEvent> findOutboxEventByAggregateId(UUID aggregateId) {
        return IChatOutboxEventRepository.findByAggregateId(aggregateId).map(ChatEntityMapper::toDomain);
    }

    @Override
    public Optional<ChatOutboxEvent> findOutboxEventByAggregateIdForUpdate(UUID aggregateId) {
        return Optional.ofNullable(IChatOutboxEventRepository.findByAggregateIdForUpdate(aggregateId))
                .map(ChatEntityMapper::toDomain);
    }

    @Override
    public List<ChatOutboxEvent> findOutboxEventsByAggregateIds(List<UUID> aggregateIds) {
        if (aggregateIds.isEmpty()) {
            return List.of();
        }
        return IChatOutboxEventRepository.findByAggregateIdIn(aggregateIds)
                .stream()
                .map(ChatEntityMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsUnreadAssistantMessageBySessionId(UUID sessionId) {
        return IChatOutboxEventRepository.existsUnreadAssistantMessageBySessionId(sessionId);
    }

    @Override
    public List<ChatOutboxEvent> lockReadyOutboxEvents(Instant now, int batchSize) {
        return IChatOutboxEventRepository.lockReadyBatch(now, batchSize)
                .stream()
                .map(ChatEntityMapper::toDomain)
                .toList();
    }

    @Override
    public long deleteOutboxEventsByStatusAndReadAtBefore(ChatOutboxEventStatus status, Instant threshold) {
        return IChatOutboxEventRepository.deleteByStatusAndReadAtBefore(status, threshold);
    }

    @Override
    public long deleteOutboxEventsByStatusInAndUpdatedAtBefore(
            Collection<ChatOutboxEventStatus> statuses,
            Instant threshold
    ) {
        return IChatOutboxEventRepository.deleteByStatusInAndUpdatedAtBefore(statuses, threshold);
    }

    private <E, D> CursorResult<D> toCursorResult(
            Slice<E> page,
            CursorQuery cursorQuery,
            java.util.function.Function<E, D> mapper
    ) {
        return new CursorResult<>(
                page.getContent().stream().map(mapper).toList(),
                page.hasNext() ? cursorQuery.offset() + page.getNumberOfElements() : null
        );
    }

    private record OffsetPageRequest(int offset, int limit) implements Pageable {

        private static OffsetPageRequest of(int offset, int limit) {
            return new OffsetPageRequest(offset, limit);
        }

        @Override
        public int getPageNumber() {
            return offset / limit;
        }

        @Override
        public int getPageSize() {
            return limit;
        }

        @Override
        public long getOffset() {
            return offset;
        }

        @Override
        public Sort getSort() {
            return Sort.unsorted();
        }

        @Override
        public Pageable next() {
            return new OffsetPageRequest(offset + limit, limit);
        }

        @Override
        public Pageable previousOrFirst() {
            return hasPrevious() ? new OffsetPageRequest(Math.max(offset - limit, 0), limit) : first();
        }

        @Override
        public Pageable first() {
            return new OffsetPageRequest(0, limit);
        }

        @Override
        public Pageable withPage(int pageNumber) {
            return new OffsetPageRequest(pageNumber * limit, limit);
        }

        @Override
        public boolean hasPrevious() {
            return offset > 0;
        }
    }
}
