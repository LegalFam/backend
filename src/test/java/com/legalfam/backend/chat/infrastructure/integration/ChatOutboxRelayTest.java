package com.legalfam.backend.chat.infrastructure.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import com.legalfam.backend.chat.application.port.in.ChatAssistantPersistenceUseCase;
import com.legalfam.backend.chat.application.port.out.ChatEventPublisherPort;
import com.legalfam.backend.chat.application.port.out.ChatPersistencePort;
import com.legalfam.backend.chat.domain.model.ChatOutboxEvent;
import com.legalfam.backend.chat.domain.model.ChatOutboxEventStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ChatOutboxRelayTest {

    @Mock
    private ChatPersistencePort chatPersistencePort;

    @Mock
    private ChatEventPublisherPort chatEventPublisherPort;

    @Mock
    private ChatAssistantPersistenceUseCase chatAssistantPersistenceUseCase;

    private ChatOutboxRelay chatOutboxRelay;

    @BeforeEach
    void setUp() {
        chatOutboxRelay = new ChatOutboxRelay(
                chatPersistencePort,
                chatEventPublisherPort,
                chatAssistantPersistenceUseCase,
                new ObjectMapper(),
                50
        );
    }

    @Test
    void relayPublishesReadyEventAndMarksItPublished() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        ChatOutboxEvent outboxEvent = readyEvent(sessionId, messageId, Instant.now().plusSeconds(3600));

        when(chatPersistencePort.lockReadyOutboxEvents(any(Instant.class), eq(50))).thenReturn(List.of(outboxEvent));
        when(chatPersistencePort.saveOutboxEvent(any(ChatOutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        chatOutboxRelay.relayReadyBatch();

        verify(chatEventPublisherPort).publishMessageQueued(new ChatMessageQueuedEvent(sessionId, messageId, "hola"));
        ArgumentCaptor<ChatOutboxEvent> eventCaptor = ArgumentCaptor.forClass(ChatOutboxEvent.class);
        verify(chatPersistencePort).saveOutboxEvent(eventCaptor.capture());
        ChatOutboxEvent savedEvent = eventCaptor.getValue();
        assertEquals(ChatOutboxEventStatus.PUBLISHED, savedEvent.getStatus());
        assertEquals(1, savedEvent.getAttemptCount());
        assertTrue(savedEvent.getPublishedAt() != null);
    }

    @Test
    void relaySchedulesRetryWhenPublishFailsBeforeExpiration() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        ChatOutboxEvent outboxEvent = readyEvent(sessionId, messageId, Instant.now().plusSeconds(3600));

        when(chatPersistencePort.lockReadyOutboxEvents(any(Instant.class), eq(50))).thenReturn(List.of(outboxEvent));
        when(chatPersistencePort.saveOutboxEvent(any(ChatOutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("Rabbit unavailable")).when(chatEventPublisherPort)
                .publishMessageQueued(any(ChatMessageQueuedEvent.class));

        chatOutboxRelay.relayReadyBatch();

        ArgumentCaptor<ChatOutboxEvent> eventCaptor = ArgumentCaptor.forClass(ChatOutboxEvent.class);
        verify(chatPersistencePort).saveOutboxEvent(eventCaptor.capture());
        ChatOutboxEvent savedEvent = eventCaptor.getValue();
        assertEquals(ChatOutboxEventStatus.FAILED, savedEvent.getStatus());
        assertEquals(1, savedEvent.getAttemptCount());
        assertTrue(savedEvent.getAvailableAt().isAfter(savedEvent.getUpdatedAt()));
    }

    @Test
    void relayMarksExpiredEventDeadAndExpiresUserMessage() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        ChatOutboxEvent outboxEvent = readyEvent(sessionId, messageId, Instant.now().minusSeconds(1));

        when(chatPersistencePort.lockReadyOutboxEvents(any(Instant.class), eq(50))).thenReturn(List.of(outboxEvent));
        when(chatPersistencePort.saveOutboxEvent(any(ChatOutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        chatOutboxRelay.relayReadyBatch();

        ArgumentCaptor<ChatOutboxEvent> eventCaptor = ArgumentCaptor.forClass(ChatOutboxEvent.class);
        verify(chatPersistencePort).saveOutboxEvent(eventCaptor.capture());
        assertEquals(ChatOutboxEventStatus.DEAD, eventCaptor.getValue().getStatus());
        verify(chatAssistantPersistenceUseCase).expireUserMessage(
                eq(messageId),
                eq("OUTBOX_EXPIRED"),
                eq("Chat processing expired before the event could be published.")
        );
    }

    private ChatOutboxEvent readyEvent(UUID sessionId, UUID messageId, Instant expiresAt) throws Exception {
        ChatOutboxEvent event = new ChatOutboxEvent();
        event.setId(UUID.randomUUID());
        event.setEventType(ChatOutboxEvent.MESSAGE_QUEUED_EVENT_TYPE);
        event.setAggregateId(messageId);
        event.setChatSessionId(sessionId);
        event.setPayload(new ObjectMapper().writeValueAsString(new ChatMessageQueuedEvent(sessionId, messageId, "hola")));
        event.setStatus(ChatOutboxEventStatus.PENDING);
        event.setAttemptCount(0);
        event.setAvailableAt(Instant.now());
        event.setExpiresAt(expiresAt);
        event.setCreatedAt(Instant.now());
        event.setUpdatedAt(Instant.now());
        return event;
    }
}
