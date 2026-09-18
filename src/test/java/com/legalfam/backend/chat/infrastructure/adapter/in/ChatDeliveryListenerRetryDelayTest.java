package com.legalfam.backend.chat.infrastructure.adapter.in;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.legalfam.backend.chat.application.event.ChatAssistantDeliveryQueuedEvent;
import com.legalfam.backend.chat.application.event.ChatAssistantErrorEvent;
import com.legalfam.backend.chat.application.event.ChatAssistantMessageEvent;
import com.legalfam.backend.chat.application.port.out.IChatAssistantDeliveryPort;
import com.legalfam.backend.chat.application.port.out.IChatPersistencePort;
import com.legalfam.backend.chat.domain.model.ChatOutboxEvent;
import com.legalfam.backend.chat.domain.model.ChatOutboxEventStatus;
import com.legalfam.backend.chat.infrastructure.config.ChatOutboxRelayProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ChatDeliveryListenerRetryDelayTest {

    private static final long RETRY_DELAY_MS = 120_000L;

    @Mock
    private IChatPersistencePort chatPersistencePort;

    @Mock
    private IChatAssistantDeliveryPort chatAssistantDeliveryPort;

    private final ChatOutboxRelayProperties properties = new ChatOutboxRelayProperties(50, RETRY_DELAY_MS);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void localListenerSchedulesRetryWithTheConfiguredDelay() {
        ChatAssistantDeliveryQueuedEvent event = queuedEvent();
        ChatOutboxEvent outboxEvent = pendingEvent(event.assistantMessageId());
        when(chatPersistencePort.findOutboxEventByAggregateIdForUpdate(event.assistantMessageId()))
                .thenReturn(Optional.of(outboxEvent));
        when(chatAssistantDeliveryPort.dispatch(any())).thenReturn(false);

        new LocalChatDeliveryListener(chatPersistencePort, chatAssistantDeliveryPort, properties).process(event);

        assertRetryDelay();
    }

    @Test
    void rabbitListenerSchedulesRetryWithTheConfiguredDelay() {
        ChatAssistantDeliveryQueuedEvent event = queuedEvent();
        ChatOutboxEvent outboxEvent = pendingEvent(event.assistantMessageId());
        when(chatPersistencePort.findOutboxEventByAggregateIdForUpdate(event.assistantMessageId()))
                .thenReturn(Optional.of(outboxEvent));
        when(chatAssistantDeliveryPort.dispatch(any())).thenReturn(false);

        new RabbitChatDeliveryListener(objectMapper, chatPersistencePort, chatAssistantDeliveryPort, properties)
                .process(objectMapper.writeValueAsString(event));

        assertRetryDelay();
    }

    @Test
    void rabbitListenerDispatchesQueuedAssistantErrorAsError() {
        UUID sessionId = UUID.randomUUID();
        ChatAssistantErrorEvent error = new ChatAssistantErrorEvent(
                sessionId,
                UUID.randomUUID(),
                "upstream_timeout",
                "Assistant service timed out",
                Instant.now(),
                "PENDING"
        );
        ChatAssistantDeliveryQueuedEvent event = ChatAssistantDeliveryQueuedEvent.ofError(UUID.randomUUID(), error);
        when(chatPersistencePort.findOutboxEventByAggregateIdForUpdate(error.messageId()))
                .thenReturn(Optional.of(pendingEvent(error.messageId())));
        when(chatAssistantDeliveryPort.dispatch(any())).thenCallRealMethod();
        when(chatAssistantDeliveryPort.dispatchAssistantError(event.userId(), sessionId, error)).thenReturn(true);

        new RabbitChatDeliveryListener(objectMapper, chatPersistencePort, chatAssistantDeliveryPort, properties)
                .process(objectMapper.writeValueAsString(event));

        verify(chatAssistantDeliveryPort, never()).dispatchAssistantMessage(any(), any(), any());
        ArgumentCaptor<ChatOutboxEvent> saved = ArgumentCaptor.forClass(ChatOutboxEvent.class);
        verify(chatPersistencePort).saveOutboxEvent(saved.capture());
        assertEquals(ChatOutboxEventStatus.PUBLISHED, saved.getValue().getStatus());
    }

    private void assertRetryDelay() {
        ArgumentCaptor<ChatOutboxEvent> saved = ArgumentCaptor.forClass(ChatOutboxEvent.class);
        verify(chatPersistencePort).saveOutboxEvent(saved.capture());

        Duration scheduled = Duration.between(Instant.now(), saved.getValue().getAvailableAt());
        assertTrue(
                scheduled.minusMillis(RETRY_DELAY_MS).abs().compareTo(Duration.ofSeconds(5)) < 0,
                "el reintento debe programarse con app.chat.outbox.relay.retry-delay-ms, y no con un valor fijo: " + scheduled
        );
    }

    private ChatAssistantDeliveryQueuedEvent queuedEvent() {
        UUID sessionId = UUID.randomUUID();
        UUID assistantMessageId = UUID.randomUUID();
        Instant now = Instant.now();
        return new ChatAssistantDeliveryQueuedEvent(
                UUID.randomUUID(),
                sessionId,
                assistantMessageId,
                new ChatAssistantMessageEvent(
                        sessionId,
                        assistantMessageId,
                        "respuesta",
                        "es",
                        null,
                        null,
                        now,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        null,
                        null,
                        "PENDING",
                        true
                ),
                null
        );
    }

    private ChatOutboxEvent pendingEvent(UUID assistantMessageId) {
        Instant now = Instant.now();
        return ChatOutboxEvent.restore(
                UUID.randomUUID(),
                ChatOutboxEvent.ASSISTANT_DELIVERY_EVENT_TYPE,
                assistantMessageId,
                UUID.randomUUID(),
                "{}",
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
