package com.legalfam.backend.chat.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessingStatus;
import com.legalfam.backend.chat.domain.model.ChatMessageRole;
import com.legalfam.backend.chat.domain.model.ChatOutboxEventStatus;
import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatCitationEntity;
import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatMessageEntity;
import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatMessageProcessingEntity;
import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatOutboxEventEntity;
import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatSessionEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatEntityMapperTest {

    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

    @Test
    void roundTripsSession() {
        ChatSessionEntity entity = new ChatSessionEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(UUID.randomUUID());
        entity.setTitle("Pensión de alimentos");
        entity.setCreatedAt(NOW);
        entity.setUpdatedAt(NOW.plusSeconds(60));

        assertThat(ChatEntityMapper.toEntity(ChatEntityMapper.toDomain(entity)))
                .usingRecursiveComparison()
                .isEqualTo(entity);
    }

    @Test
    void roundTripsMessageWithAllFields() {
        ChatMessageEntity entity = message();
        entity.setLanguage("qu");
        entity.setLanguageRequested("ay");
        entity.setContentLocalized("Kichwapi");
        entity.setNextSteps("[\"Ir al juzgado\",\"Llevar DNI\"]");
        entity.setNextStepsLocalized("[\"Rinay\"]");
        entity.setRating(5);
        entity.setFeedbackComment("Útil");
        entity.setFeedbackSubmittedAt(NOW);
        entity.setConfidenceStatus("HIGH");
        entity.setConfidenceReason("Citas verificadas");
        entity.setSpecialistSupportRecommended(true);
        entity.setCitationSupportStatus("GOOD");

        ChatMessage domain = ChatEntityMapper.toDomain(entity);

        assertThat(domain.getNextSteps()).containsExactly("Ir al juzgado", "Llevar DNI");
        assertThat(ChatEntityMapper.toEntity(domain)).usingRecursiveComparison().isEqualTo(entity);
    }

    @Test
    void keepsMissingRequestedLanguageAsNoMismatch() {
        ChatMessage domain = ChatEntityMapper.toDomain(message());

        assertThat(domain.getLanguageRequested()).isNull();
        assertThat(ChatEntityMapper.toEntity(domain).getLanguageRequested()).isNull();
    }

    @Test
    void treatsBlankOrMalformedNextStepsAsEmpty() {
        ChatMessageEntity blank = message();
        blank.setNextSteps(" ");
        ChatMessageEntity malformed = message();
        malformed.setNextSteps("{not json");

        assertThat(ChatEntityMapper.toDomain(blank).getNextSteps()).isEmpty();
        assertThat(ChatEntityMapper.toDomain(malformed).getNextSteps()).isEmpty();
        assertThat(ChatEntityMapper.toEntity(ChatEntityMapper.toDomain(blank)).getNextSteps()).isEqualTo("[]");
    }

    @Test
    void roundTripsProcessing() {
        ChatMessageProcessingEntity entity = new ChatMessageProcessingEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserMessageId(UUID.randomUUID());
        entity.setStatus(ChatMessageProcessingStatus.COMPLETED);
        entity.setErrorCode("N8N_TIMEOUT");
        entity.setErrorMessage("timeout");
        entity.setStartedAt(NOW);
        entity.setFinishedAt(NOW.plusSeconds(30));
        entity.setCreatedAt(NOW);
        entity.setUpdatedAt(NOW.plusSeconds(30));

        assertThat(ChatEntityMapper.toEntity(ChatEntityMapper.toDomain(entity)))
                .usingRecursiveComparison()
                .isEqualTo(entity);
    }

    @Test
    void roundTripsOutboxEvent() {
        ChatOutboxEventEntity entity = new ChatOutboxEventEntity();
        entity.setId(UUID.randomUUID());
        entity.setEventType("ASSISTANT_MESSAGE_READY");
        entity.setAggregateId(UUID.randomUUID());
        entity.setChatSessionId(UUID.randomUUID());
        entity.setPayload("{\"a\":1}");
        entity.setStatus(ChatOutboxEventStatus.PUBLISHED);
        entity.setAttemptCount(2);
        entity.setAvailableAt(NOW);
        entity.setPublishedAt(NOW.plusSeconds(1));
        entity.setReadAt(NOW.plusSeconds(2));
        entity.setLastError("none");
        entity.setCreatedAt(NOW);
        entity.setUpdatedAt(NOW.plusSeconds(2));

        assertThat(ChatEntityMapper.toEntity(ChatEntityMapper.toDomain(entity)))
                .usingRecursiveComparison()
                .isEqualTo(entity);
    }

    @Test
    void roundTripsCitation() {
        ChatCitationEntity entity = new ChatCitationEntity();
        entity.setId(UUID.randomUUID());
        entity.setChatMessageId(UUID.randomUUID());
        entity.setSourceTitle("Código Civil");
        entity.setSourceSnippet("resumen");
        entity.setSourceSnippetLocalized("resumen qu");
        entity.setSourceOriginalSnippet("Artículo 472");
        entity.setSourceUrl("https://example.org/cc");
        entity.setSourceLocator("Art. 472");
        entity.setSourceBreadcrumb("Libro III > Título I");
        entity.setSourceLocatorKind("ARTICLE");

        assertThat(ChatEntityMapper.toEntity(ChatEntityMapper.toDomain(entity)))
                .usingRecursiveComparison()
                .isEqualTo(entity);
    }

    private static ChatMessageEntity message() {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setId(UUID.randomUUID());
        entity.setChatSessionId(UUID.randomUUID());
        entity.setRole(ChatMessageRole.ASSISTANT);
        entity.setContent("Respuesta");
        entity.setLanguage("es");
        entity.setNextSteps("[]");
        entity.setNextStepsLocalized("[]");
        entity.setCreatedAt(NOW);
        return entity;
    }
}
