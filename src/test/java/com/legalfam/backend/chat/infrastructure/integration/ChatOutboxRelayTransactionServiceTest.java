package com.legalfam.backend.chat.infrastructure.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.legalfam.backend.chat.application.port.out.ChatPersistencePort;
import com.legalfam.backend.chat.domain.model.ChatOutboxEvent;
import com.legalfam.backend.chat.domain.model.ChatOutboxEventStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatOutboxRelayTransactionServiceTest {

    @Mock
    private ChatPersistencePort chatPersistencePort;

    private ChatOutboxRelayTransactionService service;

    @BeforeEach
    void setUp() {
        service = new ChatOutboxRelayTransactionService(chatPersistencePort);
    }

    @Test
    void claimReadyEventsMovesAvailableAtForwardBeforeReturningEvents() {
        Instant now = Instant.parse("2026-06-07T05:00:00Z");
        Duration retryDelay = Duration.ofMinutes(10);
        ChatOutboxEvent event = event(ChatOutboxEventStatus.PENDING);

        when(chatPersistencePort.lockReadyOutboxEvents(now, 50)).thenReturn(List.of(event));
        when(chatPersistencePort.saveOutboxEvent(any(ChatOutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<ChatOutboxEvent> claimed = service.claimReadyEvents(now, 50, retryDelay);

        assertEquals(1, claimed.size());
        assertSame(event, claimed.get(0));
        assertEquals(now.plus(retryDelay), event.getAvailableAt());
        assertEquals(now, event.getUpdatedAt());
        verify(chatPersistencePort).saveOutboxEvent(event);
    }

    @Test
    void recordPublishSuccessClearsLastErrorWhenEventIsUnread() {
        UUID aggregateId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-07T05:00:00Z");
        ChatOutboxEvent event = event(ChatOutboxEventStatus.PENDING);
        event.setLastError("previous failure");

        when(chatPersistencePort.findOutboxEventByAggregateIdForUpdate(aggregateId)).thenReturn(Optional.of(event));

        service.recordPublishSuccess(aggregateId, now);

        assertNull(event.getLastError());
        assertEquals(now, event.getUpdatedAt());
        verify(chatPersistencePort).saveOutboxEvent(event);
    }

    @Test
    void recordPublishFailureSchedulesRetryWhenEventIsUnread() {
        UUID aggregateId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-07T05:00:00Z");
        Duration retryDelay = Duration.ofMinutes(10);
        ChatOutboxEvent event = event(ChatOutboxEventStatus.PUBLISHED);

        when(chatPersistencePort.findOutboxEventByAggregateIdForUpdate(aggregateId)).thenReturn(Optional.of(event));

        service.recordPublishFailure(aggregateId, now, retryDelay, "Rabbit unavailable");

        assertEquals(ChatOutboxEventStatus.PENDING, event.getStatus());
        assertEquals(now.plus(retryDelay), event.getAvailableAt());
        assertEquals("Rabbit unavailable", event.getLastError());
        assertEquals(now, event.getUpdatedAt());
        verify(chatPersistencePort).saveOutboxEvent(event);
    }

    @Test
    void recordPublishFailureDoesNotModifyReadEvents() {
        UUID aggregateId = UUID.randomUUID();
        ChatOutboxEvent event = event(ChatOutboxEventStatus.READ);
        Instant originalUpdatedAt = event.getUpdatedAt();

        when(chatPersistencePort.findOutboxEventByAggregateIdForUpdate(aggregateId)).thenReturn(Optional.of(event));

        service.recordPublishFailure(aggregateId, Instant.now(), Duration.ofMinutes(10), "Rabbit unavailable");

        assertEquals(ChatOutboxEventStatus.READ, event.getStatus());
        assertEquals(originalUpdatedAt, event.getUpdatedAt());
        verify(chatPersistencePort, never()).saveOutboxEvent(any(ChatOutboxEvent.class));
    }

    @Test
    void claimReadyEventsSkipsReadEvents() {
        Instant now = Instant.parse("2026-06-07T05:00:00Z");
        ChatOutboxEvent event = event(ChatOutboxEventStatus.READ);

        when(chatPersistencePort.lockReadyOutboxEvents(now, 50)).thenReturn(List.of(event));

        service.claimReadyEvents(now, 50, Duration.ofMinutes(10));

        assertTrue(event.getAvailableAt().isBefore(now));
        verify(chatPersistencePort, never()).saveOutboxEvent(any(ChatOutboxEvent.class));
    }

    private ChatOutboxEvent event(ChatOutboxEventStatus status) {
        Instant past = Instant.parse("2026-06-07T04:00:00Z");
        ChatOutboxEvent event = new ChatOutboxEvent();
        event.setId(UUID.randomUUID());
        event.setAggregateId(UUID.randomUUID());
        event.setStatus(status);
        event.setAvailableAt(past);
        event.setUpdatedAt(past);
        return event;
    }
}
