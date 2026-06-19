package com.legalfam.backend.chat.domain.model;

import com.legalfam.backend.chat.domain.exception.InvalidChatRequestException;
import java.time.Instant;
import java.util.UUID;

public class ChatSession {

    private static final int MAX_TITLE_LENGTH = 80;

    private UUID id;
    private UUID userId;
    private String title;
    private Instant createdAt;
    private Instant updatedAt;

    private ChatSession() {
    }

    public static ChatSession create(UUID userId, Instant now) {
        ChatSession session = new ChatSession();
        session.userId = userId;
        session.createdAt = now;
        session.updatedAt = now;
        return session;
    }

    public static ChatSession restore(
            UUID id,
            UUID userId,
            String title,
            Instant createdAt,
            Instant updatedAt
    ) {
        ChatSession session = new ChatSession();
        session.id = id;
        session.userId = userId;
        session.title = title;
        session.createdAt = createdAt;
        session.updatedAt = updatedAt;
        return session;
    }

    public void recordActivity(Instant now) {
        this.updatedAt = now;
    }

    public void rename(String title, Instant now) {
        if (title == null || title.isBlank()) {
            throw new InvalidChatRequestException("Session title is required");
        }
        String normalized = title.trim();
        this.title = normalized.length() > MAX_TITLE_LENGTH
                ? normalized.substring(0, MAX_TITLE_LENGTH)
                : normalized;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }
    public UUID getUserId() {
        return userId;
    }
    public String getTitle() {
        return title;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
