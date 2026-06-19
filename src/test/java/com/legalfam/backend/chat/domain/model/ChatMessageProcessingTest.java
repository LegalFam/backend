package com.legalfam.backend.chat.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatMessageProcessingTest {

    @Test
    void startMovesQueuedProcessingToProcessingAndClearsErrors() {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant startedAt = Instant.parse("2026-01-01T00:01:00Z");
        ChatMessageProcessing processing = ChatMessageProcessing.restore(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ChatMessageProcessingStatus.QUEUED,
                "PREVIOUS_ERROR",
                "previous error",
                null,
                Instant.parse("2026-01-01T00:00:30Z"),
                createdAt,
                createdAt
        );

        assertTrue(processing.start(startedAt));

        assertEquals(ChatMessageProcessingStatus.PROCESSING, processing.getStatus());
        assertEquals(startedAt, processing.getStartedAt());
        assertNull(processing.getFinishedAt());
        assertNull(processing.getErrorCode());
        assertNull(processing.getErrorMessage());
        assertEquals(startedAt, processing.getUpdatedAt());
    }

    @Test
    void terminalProcessingCannotBeStartedOrCompletedAgain() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        ChatMessageProcessing processing = ChatMessageProcessing.queued(UUID.randomUUID(), now);
        assertTrue(processing.fail("upstream_error", "failed", now.plusSeconds(1)));

        assertFalse(processing.start(now.plusSeconds(2)));
        assertFalse(processing.complete(now.plusSeconds(3)));

        assertEquals(ChatMessageProcessingStatus.FAILED, processing.getStatus());
        assertEquals(now.plusSeconds(1), processing.getUpdatedAt());
    }
}
