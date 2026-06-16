package com.legalfam.backend.chat.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatOutboxEventTest {

    @Test
    void recordDeliveryAttemptPublishesSuccessfulDelivery() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        ChatOutboxEvent event = ChatOutboxEvent.restore(
                UUID.randomUUID(),
                ChatOutboxEvent.ASSISTANT_DELIVERY_EVENT_TYPE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "{}",
                ChatOutboxEventStatus.PENDING,
                1,
                now,
                null,
                null,
                "previous failure",
                now,
                now
        );

        assertTrue(event.recordDeliveryAttempt(true, now.plusSeconds(30), "not delivered", now.plusSeconds(1)));

        assertEquals(ChatOutboxEventStatus.PUBLISHED, event.getStatus());
        assertEquals(2, event.getAttemptCount());
        assertEquals(now.plusSeconds(1), event.getPublishedAt());
        assertNull(event.getLastError());
        assertEquals(now.plusSeconds(1), event.getUpdatedAt());
    }

    @Test
    void readOutboxEventCannotBeReservedOrFailedForRetry() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        ChatOutboxEvent event = ChatOutboxEvent.restore(
                UUID.randomUUID(),
                ChatOutboxEvent.ASSISTANT_DELIVERY_EVENT_TYPE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "{}",
                ChatOutboxEventStatus.READ,
                1,
                now,
                now,
                now,
                null,
                now,
                now
        );

        assertFalse(event.reserveForRelay(now.plusSeconds(30), now.plusSeconds(1)));
        assertFalse(event.recordRelayFailure(now.plusSeconds(30), "failed", now.plusSeconds(1)));

        assertEquals(ChatOutboxEventStatus.READ, event.getStatus());
        assertEquals(now, event.getUpdatedAt());
    }
}
