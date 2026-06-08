package com.legalfam.backend.chat.infrastructure.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.legalfam.backend.chat.application.event.ChatAssistantDeliveryQueuedEvent;
import com.legalfam.backend.chat.application.event.ChatAssistantMessageEvent;
import com.legalfam.backend.chat.application.port.out.ChatEventPublisherPort;
import com.legalfam.backend.chat.domain.model.ChatOutboxEvent;
import com.legalfam.backend.chat.domain.model.ChatOutboxEventStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ChatDeliveryRetryWorkerTest {

    @Mock
    private ChatOutboxRelayTransactionService relayTransactionService;

    @Mock
    private ChatEventPublisherPort chatEventPublisherPort;

    private ChatDeliveryRetryWorker chatDeliveryRetryWorker;

    @BeforeEach
    void setUp() {
        chatDeliveryRetryWorker = new ChatDeliveryRetryWorker(
                relayTransactionService,
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

        when(relayTransactionService.claimReadyEvents(any(Instant.class), eq(50), eq(Duration.ofMillis(600000))))
                .thenReturn(List.of(outboxEvent));

        chatDeliveryRetryWorker.relayReadyEvents();

        ArgumentCaptor<ChatAssistantDeliveryQueuedEvent> payloadCaptor =
                ArgumentCaptor.forClass(ChatAssistantDeliveryQueuedEvent.class);
        InOrder order = inOrder(relayTransactionService, chatEventPublisherPort);
        order.verify(relayTransactionService).claimReadyEvents(any(Instant.class), eq(50), eq(Duration.ofMillis(600000)));
        order.verify(chatEventPublisherPort).publishAssistantDelivery(payloadCaptor.capture());
        order.verify(relayTransactionService).recordPublishSuccess(eq(assistantMessageId), any(Instant.class));

        assertEquals(assistantMessageId, payloadCaptor.getValue().assistantMessageId());
    }

    @Test
    void relaySchedulesRetryWhenPublishFails() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID assistantMessageId = UUID.randomUUID();
        ChatOutboxEvent outboxEvent = readyEvent(userId, sessionId, assistantMessageId);

        when(relayTransactionService.claimReadyEvents(any(Instant.class), eq(50), eq(Duration.ofMillis(600000))))
                .thenReturn(List.of(outboxEvent));
        doThrow(new IllegalStateException("Rabbit unavailable")).when(chatEventPublisherPort)
                .publishAssistantDelivery(any(ChatAssistantDeliveryQueuedEvent.class));

        chatDeliveryRetryWorker.relayReadyEvents();

        verify(relayTransactionService).recordPublishFailure(
                eq(assistantMessageId),
                any(Instant.class),
                eq(Duration.ofMillis(600000)),
                eq("Rabbit unavailable")
        );
    }

    private ChatOutboxEvent readyEvent(UUID userId, UUID sessionId, UUID assistantMessageId) throws Exception {
        ChatAssistantMessageEvent assistantMessageEvent = new ChatAssistantMessageEvent(
                sessionId,
                assistantMessageId,
                "hola",
                Instant.now(),
                List.of(),
                null,
                null,
                List.of(),
                null,
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
