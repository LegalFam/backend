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

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
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
}
