package com.legalfam.backend.chat.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
                () -> message.applyAssistantMetadata("LOW", "reason", List.of("step"), List.of(), true, "GOOD")
        );
    }

    @Test
    void applyAssistantMetadataNormalizesCitationSupportStatus() {
        ChatMessage message = ChatMessage.assistantMessage(
                UUID.randomUUID(),
                "answer",
                Instant.parse("2026-01-01T00:00:00Z")
        );

        message.applyAssistantMetadata("LOW", "reason", List.of("step"), List.of(), true, "weak");

        assertEquals("WEAK", message.getCitationSupportStatus());

        message.applyAssistantMetadata("LOW", "reason", List.of("step"), List.of(), true, "unsupported");

        assertNull(message.getCitationSupportStatus());
    }

    @Test
    void userMessageInSpanishKeepsNoLocalizedCopy() {
        ChatMessage message = ChatMessage.userMessage(
                UUID.randomUUID(),
                "quiero pension de alimentos",
                ChatLanguage.ES,
                Instant.parse("2026-01-01T00:00:00Z")
        );

        assertEquals(ChatLanguage.ES, message.getLanguage());
        assertEquals("quiero pension de alimentos", message.getContent());
        assertNull(message.getContentLocalized());
    }

    @Test
    void applyTranslatedContentMovesOriginalIntoLocalizedSlot() {
        // El mensaje se guarda antes de que exista su traduccion, asi que el original ocupa
        // `content` de forma provisional. Cuando llega el espanol, este pasa a ser el
        // canonico y el original queda visible para el usuario.
        ChatMessage message = ChatMessage.userMessage(
                UUID.randomUUID(),
                "wawaypaq mikuy qullqita mañakuyta munani",
                ChatLanguage.QU,
                Instant.parse("2026-01-01T00:00:00Z")
        );

        message.applyTranslatedContent("quiero pedir pension de alimentos para mi hijo");

        assertEquals("quiero pedir pension de alimentos para mi hijo", message.getContent());
        assertEquals("wawaypaq mikuy qullqita mañakuyta munani", message.getContentLocalized());
    }

    @Test
    void applyTranslatedContentIsIgnoredWhenTranslationIsMissing() {
        // Sin traduccion el turno sigue siendo legible en su lengua original: peor historial
        // para el siguiente turno, pero nunca un mensaje perdido.
        ChatMessage message = ChatMessage.userMessage(
                UUID.randomUUID(),
                "wawaypaq mikuy qullqita",
                ChatLanguage.QU,
                Instant.parse("2026-01-01T00:00:00Z")
        );

        message.applyTranslatedContent("   ");

        assertEquals("wawaypaq mikuy qullqita", message.getContent());
    }

    @Test
    void applyTranslatedContentNeverTouchesSpanishMessages() {
        ChatMessage message = ChatMessage.userMessage(
                UUID.randomUUID(),
                "original en espanol",
                ChatLanguage.ES,
                Instant.parse("2026-01-01T00:00:00Z")
        );

        message.applyTranslatedContent("otra cosa");

        assertEquals("original en espanol", message.getContent());
        assertNull(message.getContentLocalized());
    }

    @Test
    void assistantMetadataDropsLocalizedStepsInSpanishConversations() {
        ChatMessage message = ChatMessage.assistantMessage(
                UUID.randomUUID(),
                "answer",
                Instant.parse("2026-01-01T00:00:00Z")
        );

        message.applyAssistantMetadata("HIGH", "reason", List.of("paso"), List.of("no aplica"), false, "GOOD");

        assertEquals(List.of(), message.getNextStepsLocalized());
    }
}
