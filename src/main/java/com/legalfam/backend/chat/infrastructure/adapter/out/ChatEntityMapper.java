package com.legalfam.backend.chat.infrastructure.adapter.out;

import com.legalfam.backend.chat.domain.model.ChatCitation;
import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessing;
import com.legalfam.backend.chat.domain.model.ChatOutboxEvent;
import com.legalfam.backend.chat.domain.model.ChatSession;
import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatCitationEntity;
import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatMessageEntity;
import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatMessageProcessingEntity;
import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatOutboxEventEntity;
import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatSessionEntity;
import java.util.List;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

final class ChatEntityMapper {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private ChatEntityMapper() {
    }

    static ChatSession toDomain(ChatSessionEntity entity) {
        return ChatSession.restore(
                entity.getId(),
                entity.getUserId(),
                entity.getTitle(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    static ChatSessionEntity toEntity(ChatSession domain) {
        ChatSessionEntity entity = new ChatSessionEntity();
        entity.setId(domain.getId());
        entity.setUserId(domain.getUserId());
        entity.setTitle(domain.getTitle());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }

    static ChatMessage toDomain(ChatMessageEntity entity) {
        return ChatMessage.restore(
                entity.getId(),
                entity.getChatSessionId(),
                entity.getRole(),
                entity.getContent(),
                entity.getErrorCode(),
                entity.getRating(),
                entity.getFeedbackComment(),
                entity.getFeedbackSubmittedAt(),
                entity.getConfidenceStatus(),
                entity.getConfidenceReason(),
                readStringList(entity.getNextSteps()),
                entity.getSpecialistSupportRecommended(),
                entity.getCitationSupportStatus(),
                entity.getCreatedAt()
        );
    }

    static ChatMessageEntity toEntity(ChatMessage domain) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setId(domain.getId());
        entity.setChatSessionId(domain.getChatSessionId());
        entity.setRole(domain.getRole());
        entity.setContent(domain.getContent());
        entity.setErrorCode(domain.getErrorCode());
        entity.setRating(domain.getRating());
        entity.setFeedbackComment(domain.getFeedbackComment());
        entity.setFeedbackSubmittedAt(domain.getFeedbackSubmittedAt());
        entity.setConfidenceStatus(domain.getConfidenceStatus());
        entity.setConfidenceReason(domain.getConfidenceReason());
        entity.setNextSteps(writeStringList(domain.getNextSteps()));
        entity.setSpecialistSupportRecommended(domain.getSpecialistSupportRecommended());
        entity.setCitationSupportStatus(domain.getCitationSupportStatus());
        entity.setCreatedAt(domain.getCreatedAt());
        return entity;
    }

    static ChatMessageProcessing toDomain(ChatMessageProcessingEntity entity) {
        return ChatMessageProcessing.restore(
                entity.getId(),
                entity.getUserMessageId(),
                entity.getStatus(),
                entity.getErrorCode(),
                entity.getErrorMessage(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    static ChatMessageProcessingEntity toEntity(ChatMessageProcessing domain) {
        ChatMessageProcessingEntity entity = new ChatMessageProcessingEntity();
        entity.setId(domain.getId());
        entity.setUserMessageId(domain.getUserMessageId());
        entity.setStatus(domain.getStatus());
        entity.setErrorCode(domain.getErrorCode());
        entity.setErrorMessage(domain.getErrorMessage());
        entity.setStartedAt(domain.getStartedAt());
        entity.setFinishedAt(domain.getFinishedAt());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }

    static ChatOutboxEvent toDomain(ChatOutboxEventEntity entity) {
        return ChatOutboxEvent.restore(
                entity.getId(),
                entity.getEventType(),
                entity.getAggregateId(),
                entity.getChatSessionId(),
                entity.getPayload(),
                entity.getStatus(),
                entity.getAttemptCount(),
                entity.getAvailableAt(),
                entity.getPublishedAt(),
                entity.getReadAt(),
                entity.getLastError(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    static ChatOutboxEventEntity toEntity(ChatOutboxEvent domain) {
        ChatOutboxEventEntity entity = new ChatOutboxEventEntity();
        entity.setId(domain.getId());
        entity.setEventType(domain.getEventType());
        entity.setAggregateId(domain.getAggregateId());
        entity.setChatSessionId(domain.getChatSessionId());
        entity.setPayload(domain.getPayload());
        entity.setStatus(domain.getStatus());
        entity.setAttemptCount(domain.getAttemptCount());
        entity.setAvailableAt(domain.getAvailableAt());
        entity.setPublishedAt(domain.getPublishedAt());
        entity.setReadAt(domain.getReadAt());
        entity.setLastError(domain.getLastError());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }

    static ChatCitation toDomain(ChatCitationEntity entity) {
        return ChatCitation.restore(
                entity.getId(),
                entity.getChatMessageId(),
                entity.getSourceTitle(),
                entity.getSourceSnippet(),
                entity.getSourceUrl()
        );
    }

    static ChatCitationEntity toEntity(ChatCitation domain) {
        ChatCitationEntity entity = new ChatCitationEntity();
        entity.setId(domain.getId());
        entity.setChatMessageId(domain.getChatMessageId());
        entity.setSourceTitle(domain.getSourceTitle());
        entity.setSourceSnippet(domain.getSourceSnippet());
        entity.setSourceUrl(domain.getSourceUrl());
        return entity;
    }

    private static List<String> readStringList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(
                    value,
                    OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, String.class)
            );
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static String writeStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(values);
        } catch (Exception ignored) {
            return "[]";
        }
    }
}
