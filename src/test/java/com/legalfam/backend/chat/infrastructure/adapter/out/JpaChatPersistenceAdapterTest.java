package com.legalfam.backend.chat.infrastructure.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessingStatus;
import com.legalfam.backend.chat.domain.model.ChatMessageRole;
import com.legalfam.backend.chat.domain.model.ChatOutboxEventStatus;
import com.legalfam.backend.chat.domain.model.ChatSession;
import com.legalfam.backend.chat.infrastructure.persistence.IChatCitationRepository;
import com.legalfam.backend.chat.infrastructure.persistence.IChatMessageProcessingRepository;
import com.legalfam.backend.chat.infrastructure.persistence.IChatMessageRepository;
import com.legalfam.backend.chat.infrastructure.persistence.IChatOutboxEventRepository;
import com.legalfam.backend.chat.infrastructure.persistence.IChatSessionRepository;
import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatMessageEntity;
import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatMessageProcessingEntity;
import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatOutboxEventEntity;
import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatSessionEntity;
import com.legalfam.backend.common.cursor.CursorQuery;
import com.legalfam.backend.common.cursor.CursorResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

class JpaChatPersistenceAdapterTest {

    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

    private final IChatSessionRepository sessions = mock(IChatSessionRepository.class);
    private final IChatMessageRepository messages = mock(IChatMessageRepository.class);
    private final IChatCitationRepository citations = mock(IChatCitationRepository.class);
    private final IChatMessageProcessingRepository processing = mock(IChatMessageProcessingRepository.class);
    private final IChatOutboxEventRepository outbox = mock(IChatOutboxEventRepository.class);
    private final JpaChatPersistenceAdapter adapter =
            new JpaChatPersistenceAdapter(sessions, messages, citations, processing, outbox);

    @Test
    void deletesChildrenBeforeTheSession() {
        UUID sessionId = UUID.randomUUID();
        ChatMessageEntity first = message(sessionId, NOW);
        ChatMessageEntity second = message(sessionId, NOW.plusSeconds(1));
        when(messages.findByChatSessionIdOrderByCreatedAtAsc(sessionId)).thenReturn(List.of(first, second));

        adapter.deleteSessionById(sessionId);

        InOrder order = inOrder(citations, processing, outbox, messages, sessions);
        order.verify(citations).deleteByChatMessageIdIn(List.of(first.getId(), second.getId()));
        order.verify(processing).deleteByUserMessageIdIn(List.of(first.getId(), second.getId()));
        order.verify(outbox).deleteByChatSessionId(sessionId);
        order.verify(messages).deleteByChatSessionId(sessionId);
        order.verify(sessions).deleteById(sessionId);
    }

    @Test
    void skipsMessageChildrenForAnEmptySession() {
        UUID sessionId = UUID.randomUUID();
        when(messages.findByChatSessionIdOrderByCreatedAtAsc(sessionId)).thenReturn(List.of());

        adapter.deleteSessionById(sessionId);

        verifyNoInteractions(citations, processing);
        verify(sessions).deleteById(sessionId);
    }

    @Test
    void returnsNextCursorOnlyWhenThereAreMoreSessions() {
        UUID userId = UUID.randomUUID();
        ChatSessionEntity entity = session(userId);
        when(sessions.findByUserIdOrderByUpdatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(entity, session(userId)), Pageable.ofSize(2), true))
                .thenReturn(new SliceImpl<>(List.of(entity), Pageable.ofSize(2), false));

        CursorResult<ChatSession> firstPage = adapter.findSessionsByUserIdOrderByUpdatedAtDesc(userId, CursorQuery.of(10, 2));
        CursorResult<ChatSession> lastPage = adapter.findSessionsByUserIdOrderByUpdatedAtDesc(userId, CursorQuery.of(12, 2));

        assertThat(firstPage.nextCursor()).isEqualTo(12);
        assertThat(firstPage.content()).first().extracting(ChatSession::getId).isEqualTo(entity.getId());
        assertThat(lastPage.nextCursor()).isNull();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(sessions, org.mockito.Mockito.times(2)).findByUserIdOrderByUpdatedAtDesc(eq(userId), pageable.capture());
        Pageable request = pageable.getAllValues().get(0);
        assertThat(request.getOffset()).isEqualTo(10);
        assertThat(request.getPageSize()).isEqualTo(2);
        assertThat(request.getPageNumber()).isEqualTo(5);
        assertThat(request.hasPrevious()).isTrue();
        assertThat(request.next().getOffset()).isEqualTo(12);
        assertThat(request.previousOrFirst().getOffset()).isEqualTo(8);
        assertThat(request.first().getOffset()).isZero();
        assertThat(request.withPage(3).getOffset()).isEqualTo(6);
        assertThat(request.getSort().isUnsorted()).isTrue();
    }

    @Test
    void filtersSessionsByHistoryWindow() {
        UUID userId = UUID.randomUUID();
        when(sessions.findByUserIdAndUpdatedAtAfterOrderByUpdatedAtDesc(eq(userId), eq(NOW), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(session(userId))));

        assertThat(adapter.findSessionsByUserIdUpdatedAfterOrderByUpdatedAtDesc(userId, NOW, CursorQuery.of(null, 20))
                .content()).hasSize(1);
    }

    @Test
    void returnsAssistantContextOldestFirst() {
        UUID sessionId = UUID.randomUUID();
        ChatMessageEntity newer = message(sessionId, NOW.plusSeconds(10));
        ChatMessageEntity older = message(sessionId, NOW);
        when(messages.findByChatSessionIdAndRoleInOrderByCreatedAtDesc(
                eq(sessionId), eq(List.of(ChatMessageRole.USER, ChatMessageRole.ASSISTANT)), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(newer, older)));

        List<ChatMessage> context = adapter.findRecentMessagesForAssistantContext(sessionId, 2);

        assertThat(context).extracting(ChatMessage::getId).containsExactly(older.getId(), newer.getId());
        assertThat(adapter.findRecentMessagesForAssistantContext(sessionId, 0)).isEmpty();
    }

    @Test
    void avoidsQueriesForEmptyInputs() {
        assertThat(adapter.saveCitations(List.of())).isEmpty();
        assertThat(adapter.findOutboxEventsByAggregateIds(List.of())).isEmpty();

        verify(citations, never()).saveAll(anyList());
        verify(outbox, never()).findByAggregateIdIn(anyList());
    }

    @Test
    void returnsTheFirstActiveProcessingOfTheUser() {
        UUID userId = UUID.randomUUID();
        ChatMessageProcessingEntity active = new ChatMessageProcessingEntity();
        active.setId(UUID.randomUUID());
        active.setUserMessageId(UUID.randomUUID());
        active.setStatus(ChatMessageProcessingStatus.PROCESSING);
        when(processing.findActiveByUserId(eq(userId), anyList(), any(Pageable.class))).thenReturn(List.of(active));

        assertThat(adapter.findActiveMessageProcessingByUserId(userId))
                .get()
                .satisfies(found -> assertThat(found.getId()).isEqualTo(active.getId()));
        assertThat(adapter.findActiveMessageProcessingByUserId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void wrapsMissingLockedRowsAsEmpty() {
        UUID id = UUID.randomUUID();
        when(processing.findByUserMessageIdForUpdate(id)).thenReturn(null);
        when(outbox.findByAggregateIdForUpdate(id)).thenReturn(null);

        assertThat(adapter.findMessageProcessingByUserMessageIdForUpdate(id)).isEmpty();
        assertThat(adapter.findOutboxEventByAggregateIdForUpdate(id)).isEmpty();
    }

    @Test
    void mapsOutboxQueries() {
        ChatOutboxEventEntity event = new ChatOutboxEventEntity();
        event.setId(UUID.randomUUID());
        event.setAggregateId(UUID.randomUUID());
        event.setStatus(ChatOutboxEventStatus.PENDING);
        when(outbox.lockReadyBatch(NOW, 5)).thenReturn(List.of(event));
        when(outbox.findByAggregateIdIn(List.of(event.getAggregateId()))).thenReturn(List.of(event));
        when(outbox.deleteByStatusAndReadAtBefore(ChatOutboxEventStatus.READ, NOW)).thenReturn(3L);
        when(sessions.findById(any())).thenReturn(Optional.empty());

        assertThat(adapter.lockReadyOutboxEvents(NOW, 5)).hasSize(1);
        assertThat(adapter.findOutboxEventsByAggregateIds(List.of(event.getAggregateId()))).hasSize(1);
        assertThat(adapter.deleteOutboxEventsByStatusAndReadAtBefore(ChatOutboxEventStatus.READ, NOW)).isEqualTo(3L);
        assertThat(adapter.findSessionById(UUID.randomUUID())).isEmpty();
    }

    private static ChatSessionEntity session(UUID userId) {
        ChatSessionEntity entity = new ChatSessionEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setTitle("Consulta");
        entity.setCreatedAt(NOW);
        entity.setUpdatedAt(NOW);
        return entity;
    }

    private static ChatMessageEntity message(UUID sessionId, Instant createdAt) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setId(UUID.randomUUID());
        entity.setChatSessionId(sessionId);
        entity.setRole(ChatMessageRole.USER);
        entity.setContent("Hola");
        entity.setLanguage("es");
        entity.setCreatedAt(createdAt);
        return entity;
    }
}
