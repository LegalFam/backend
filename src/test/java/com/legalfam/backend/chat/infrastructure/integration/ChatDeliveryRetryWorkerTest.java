package com.legalfam.backend.chat.infrastructure.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.legalfam.backend.chat.application.event.ChatAssistantDeliveryQueuedEvent;
import com.legalfam.backend.chat.application.event.ChatAssistantMessageEvent;
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
class ChatDeliveryRetryWorkerTest {

    @Mock
    private ChatPersistencePort chatPersistencePort;

    @Mock
    private ChatEventPublisherPort chatEventPublisherPort;

    private ChatDeliveryRetryWorker chatDeliveryRetryWorker;

    @BeforeEach
    void setUp() {
        chatDeliveryRetryWorker = new ChatDeliveryRetryWorker(
                chatPersistencePort,
                chatEventPublisherPort,
                new ObjectMapper(),
                50,
                600000
        );
    }

    @Test
    void relayPublishesReadyEventAndMarksItPublished() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID assistantMessageId = UUID.randomUUID();
        ChatOutboxEvent outboxEvent = readyEvent(userId, sessionId, assistantMessageId);

        when(chatPersistencePort.lockReadyOutboxEvents(any(Instant.class), eq(50))).thenReturn(List.of(outboxEvent));
        when(chatPersistencePort.saveOutboxEvent(any(ChatOutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        chatDeliveryRetryWorker.relayReadyEvents();

        verify(chatEventPublisherPort).publishAssistantDelivery(any(ChatAssistantDeliveryQueuedEvent.class));
        ArgumentCaptor<ChatOutboxEvent> eventCaptor = ArgumentCaptor.forClass(ChatOutboxEvent.class);
        verify(chatPersistencePort).saveOutboxEvent(eventCaptor.capture());
        ChatOutboxEvent savedEvent = eventCaptor.getValue();
        assertEquals(ChatOutboxEventStatus.PENDING, savedEvent.getStatus());
        assertTrue(savedEvent.getAvailableAt().isAfter(savedEvent.getUpdatedAt()));
    }

    @Test
    void relaySchedulesRetryWhenPublishFails() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID assistantMessageId = UUID.randomUUID();
        ChatOutboxEvent outboxEvent = readyEvent(userId, sessionId, assistantMessageId);

        when(chatPersistencePort.lockReadyOutboxEvents(any(Instant.class), eq(50))).thenReturn(List.of(outboxEvent));
        when(chatPersistencePort.saveOutboxEvent(any(ChatOutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("Rabbit unavailable")).when(chatEventPublisherPort)
                .publishAssistantDelivery(any(ChatAssistantDeliveryQueuedEvent.class));

        chatDeliveryRetryWorker.relayReadyEvents();

        ArgumentCaptor<ChatOutboxEvent> eventCaptor = ArgumentCaptor.forClass(ChatOutboxEvent.class);
        verify(chatPersistencePort).saveOutboxEvent(eventCaptor.capture());
        ChatOutboxEvent savedEvent = eventCaptor.getValue();
        assertEquals(ChatOutboxEventStatus.PENDING, savedEvent.getStatus());
        assertTrue(savedEvent.getAvailableAt().isAfter(savedEvent.getUpdatedAt()));
    }

    private ChatOutboxEvent readyEvent(UUID userId, UUID sessionId, UUID assistantMessageId) throws Exception {
        ChatAssistantMessageEvent assistantMessageEvent = new ChatAssistantMessageEvent(
                sessionId,
                assistantMessageId,
                "hola",
                Instant.now(),
                List.of(),
                "PENDING",
                true
        );
        ChatAssistantDeliveryQueuedEvent deliveryEvent = new ChatAssistantDeliveryQueuedEvent(
                userId,
                sessionId,
                assistantMessageId,
                assistantMessageEvent
        );

        ChatOutboxEvent event = new ChatOutboxEvent();
        event.setId(UUID.randomUUID());
        event.setEventType(ChatOutboxEvent.ASSISTANT_DELIVERY_EVENT_TYPE);
        event.setAggregateId(assistantMessageId);
        event.setChatSessionId(sessionId);
        event.setPayload(new ObjectMapper().writeValueAsString(deliveryEvent));
        event.setStatus(ChatOutboxEventStatus.PENDING);
        event.setAttemptCount(0);
        event.setAvailableAt(Instant.now());
        event.setCreatedAt(Instant.now());
        event.setUpdatedAt(Instant.now());
        return event;
    }
}
