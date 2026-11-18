package com.legalfam.backend.chat.application.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.legalfam.backend.chat.application.dto.ChatMessageResponse;
import com.legalfam.backend.chat.domain.model.ChatCitation;
import com.legalfam.backend.chat.domain.model.ChatLanguage;
import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatMessageRole;
import com.legalfam.backend.chat.domain.model.ChatOutboxEvent;
import com.legalfam.backend.chat.domain.model.ChatOutboxEventStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatMessageResponseMapperTest {

    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
    private final ChatMessageResponseMapper mapper = new ChatMessageResponseMapper();

    @Test
    void mapsMessageWithCitationsAndReadReceipt() {
        ChatMessage message = message(ChatLanguage.fromCode("qu"), ChatLanguage.fromCode("ay"));
        ChatCitation citation = ChatCitation.restore(
                UUID.randomUUID(), message.getId(), "Código Civil", "resumen", "resumen qu",
                "Artículo 472", "https://example.org/cc", "Art. 472", "Libro III", "ARTICLE"
        );
        ChatOutboxEvent event = ChatOutboxEvent.restore(
                UUID.randomUUID(), "ASSISTANT_MESSAGE_READY", message.getId(), message.getChatSessionId(),
                "{}", ChatOutboxEventStatus.READ, 1, NOW, NOW, NOW.plusSeconds(5), null, NOW, NOW
        );

        ChatMessageResponse response = mapper.toResponse(
                message,
                Map.of(message.getId(), List.of(citation)),
                Map.of(message.getId(), event)
        );

        assertThat(response.id()).isEqualTo(message.getId());
        assertThat(response.role()).isEqualTo("ASSISTANT");
        assertThat(response.language()).isEqualTo("qu");
        assertThat(response.languageRequested()).isEqualTo("ay");
        assertThat(response.nextSteps()).containsExactly("Ir al juzgado");
        assertThat(response.receiptStatus()).isEqualTo("READ");
        assertThat(response.readAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(response.citations()).singleElement().satisfies(c -> {
            assertThat(c.sourceTitle()).isEqualTo("Código Civil");
            assertThat(c.sourceLocator()).isEqualTo("Art. 472");
            assertThat(c.sourceLocatorKind()).isEqualTo("ARTICLE");
        });
    }

    @Test
    void leavesOptionalFieldsEmptyWithoutCitationsOrOutbox() {
        ChatMessageResponse response = mapper.toResponse(message(ChatLanguage.ES, null), Map.of(), Map.of());

        assertThat(response.languageRequested()).isNull();
        assertThat(response.citations()).isEmpty();
        assertThat(response.receiptStatus()).isNull();
        assertThat(response.readAt()).isNull();
    }

    private static ChatMessage message(ChatLanguage language, ChatLanguage requested) {
        return ChatMessage.restore(
                UUID.randomUUID(), UUID.randomUUID(), ChatMessageRole.ASSISTANT, "Respuesta",
                language, requested, null, null, null, null, null, "HIGH", null,
                List.of("Ir al juzgado"), List.of(), false, "GOOD", NOW
        );
    }
}
