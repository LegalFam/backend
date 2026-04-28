package com.legalfam.backend.chat.infrastructure.adapter.persistence;

import com.legalfam.backend.chat.domain.model.ChatCitation;
import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatSession;
import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatCitationEntity;
import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatMessageEntity;
import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatSessionEntity;

final class ChatEntityMapper {

    private ChatEntityMapper() {
    }

    static ChatSession toDomain(ChatSessionEntity entity) {
        ChatSession session = new ChatSession();
        session.setId(entity.getId());
        session.setUserId(entity.getUserId());
        session.setCreatedAt(entity.getCreatedAt());
        session.setUpdatedAt(entity.getUpdatedAt());
        return session;
    }

    static ChatSessionEntity toEntity(ChatSession domain) {
        ChatSessionEntity entity = new ChatSessionEntity();
        entity.setId(domain.getId());
        entity.setUserId(domain.getUserId());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }

    static ChatMessage toDomain(ChatMessageEntity entity) {
        ChatMessage message = new ChatMessage();
        message.setId(entity.getId());
        message.setChatSessionId(entity.getChatSessionId());
        message.setRole(entity.getRole());
        message.setContent(entity.getContent());
        message.setRating(entity.getRating());
        message.setCreatedAt(entity.getCreatedAt());
        return message;
    }

    static ChatMessageEntity toEntity(ChatMessage domain) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setId(domain.getId());
        entity.setChatSessionId(domain.getChatSessionId());
        entity.setRole(domain.getRole());
        entity.setContent(domain.getContent());
        entity.setRating(domain.getRating());
        entity.setCreatedAt(domain.getCreatedAt());
        return entity;
    }

    static ChatCitation toDomain(ChatCitationEntity entity) {
        ChatCitation citation = new ChatCitation();
        citation.setId(entity.getId());
        citation.setChatMessageId(entity.getChatMessageId());
        citation.setSourceTitle(entity.getSourceTitle());
        citation.setSourceSnippet(entity.getSourceSnippet());
        citation.setSourceUrl(entity.getSourceUrl());
        return citation;
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
}
