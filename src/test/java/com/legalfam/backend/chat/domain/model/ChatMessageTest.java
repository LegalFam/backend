package com.legalfam.backend.chat.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.legalfam.backend.chat.domain.exception.InvalidChatRequestException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatMessageTest {

    @Test
    void submitFeedbackNormalizesCommentForAssistantMessage() {
        ChatMessage message = ChatMessage.assistantMessage(
                UUID.randomUUID(),
                "answer",
                Instant.parse("2026-01-01T00:00:00Z")
        );

        message.submitFeedback(5, "  useful  ", Instant.parse("2026-01-01T00:01:00Z"));

        assertEquals(5, message.getRating());
        assertEquals("useful", message.getFeedbackComment());
        assertEquals(Instant.parse("2026-01-01T00:01:00Z"), message.getFeedbackSubmittedAt());
    }

    @Test
    void submitFeedbackRejectsUserMessages() {
        ChatMessage message = ChatMessage.userMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "question",
                Instant.parse("2026-01-01T00:00:00Z")
        );

        assertThrows(InvalidChatRequestException.class, () -> message.submitFeedback(5, null, Instant.now()));
    }

    @Test
    void applyAssistantMetadataRejectsNonAssistantMessages() {
        ChatMessage message = ChatMessage.systemMessage(
                UUID.randomUUID(),
                "error",
                Instant.parse("2026-01-01T00:00:00Z")
        );

        assertThrows(
                InvalidChatRequestException.class,
                () -> message.applyAssistantMetadata("LOW", "reason", List.of("step"), true)
        );
    }
}
