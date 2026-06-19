package com.legalfam.backend.chat.infrastructure.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.legalfam.backend.chat.application.event.ChatAssistantDeliveryQueuedEvent;
import com.legalfam.backend.chat.application.event.ChatAssistantMessageEvent;
import com.legalfam.backend.chat.application.port.out.IChatEventPublisherPort;
import com.legalfam.backend.chat.domain.model.ChatOutboxEvent;
import com.legalfam.backend.chat.domain.model.ChatOutboxEventStatus;
import com.legalfam.backend.chat.infrastructure.config.ChatOutboxRelayProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.legalfam.backend.chat.infrastructure.adapter.out.ChatOutboxRelayTransactionService;
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
    private IChatEventPublisherPort IChatEventPublisherPort;

    private ChatDeliveryRetryWorker chatDeliveryRetryWorker;

    @BeforeEach
    void setUp() {
        chatDeliveryRetryWorker = new ChatDeliveryRetryWorker(
                relayTransactionService,
                IChatEventPublisherPort,
                new ObjectMapper(),
                new ChatOutboxRelayProperties(50, 600000L)
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
        InOrder order = inOrder(relayTransactionService, IChatEventPublisherPort);
        order.verify(relayTransactionService).claimReadyEvents(any(Instant.class), eq(50), eq(Duration.ofMillis(600000)));
        order.verify(IChatEventPublisherPort).publishAssistantDelivery(payloadCaptor.capture());
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
        doThrow(new IllegalStateException("Rabbit unavailable")).when(IChatEventPublisherPort)
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

        Instant now = Instant.now();
        return ChatOutboxEvent.restore(
                UUID.randomUUID(),
                ChatOutboxEvent.ASSISTANT_DELIVERY_EVENT_TYPE,
                assistantMessageId,
                sessionId,
                new ObjectMapper().writeValueAsString(deliveryEvent),
                ChatOutboxEventStatus.PENDING,
                0,
                now,
                null,
                null,
                null,
                now,
                now
        );
    }
}
