package com.legalfam.backend.chat.domain.model;

import java.time.Instant;
import java.util.UUID;

public class ChatOutboxEvent {

    public static final String ASSISTANT_DELIVERY_EVENT_TYPE = "chat.assistant.delivery.v1";

    private UUID id;
    private String eventType;
    private UUID aggregateId;
    private UUID chatSessionId;
    private String payload;
    private ChatOutboxEventStatus status;
    private int attemptCount;
    private Instant availableAt;
    private Instant publishedAt;
    private Instant readAt;
    private String lastError;
    private Instant createdAt;
    private Instant updatedAt;

    public static ChatOutboxEvent restore(
            UUID id,
            String eventType,
            UUID aggregateId,
            UUID chatSessionId,
            String payload,
            ChatOutboxEventStatus status,
            int attemptCount,
            Instant availableAt,
            Instant publishedAt,
            Instant readAt,
            String lastError,
            Instant createdAt,
            Instant updatedAt
    ) {
        ChatOutboxEvent event = new ChatOutboxEvent();
        event.id = id;
        event.eventType = eventType;
        event.aggregateId = aggregateId;
        event.chatSessionId = chatSessionId;
        event.payload = payload;
        event.status = status;
        event.attemptCount = attemptCount;
        event.availableAt = availableAt;
        event.publishedAt = publishedAt;
        event.readAt = readAt;
        event.lastError = lastError;
        event.createdAt = createdAt;
        event.updatedAt = updatedAt;
        return event;
    }

    public boolean isRead() {
        return status == ChatOutboxEventStatus.READ;
    }

    public void registerAssistantDelivery(UUID aggregateId, UUID chatSessionId, String payload, Instant now) {
        this.eventType = ASSISTANT_DELIVERY_EVENT_TYPE;
        this.aggregateId = aggregateId;
        this.chatSessionId = chatSessionId;
        this.payload = payload;
        if (status == null || isRead()) {
            status = ChatOutboxEventStatus.PENDING;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        availableAt = now;
        updatedAt = now;
        if (publishedAt == null) {
            attemptCount = 0;
        }
    }

    public boolean reserveForRelay(Instant nextAvailableAt, Instant now) {
        if (isRead()) {
            return false;
        }
        availableAt = nextAvailableAt;
        updatedAt = now;
        return true;
    }

    public boolean recordRelaySuccess(Instant now) {
        if (isRead()) {
            return false;
        }
        lastError = null;
        updatedAt = now;
        return true;
    }

    public boolean recordRelayFailure(Instant nextAvailableAt, String errorMessage, Instant now) {
        if (isRead()) {
            return false;
        }
        status = ChatOutboxEventStatus.PENDING;
        availableAt = nextAvailableAt;
        lastError = errorMessage;
        updatedAt = now;
        return true;
    }

    public boolean recordDeliveryAttempt(boolean delivered, Instant nextAvailableAt, String failureMessage, Instant now) {
        if (isRead()) {
            return false;
        }
        attemptCount += 1;
        availableAt = nextAvailableAt;
        status = delivered ? ChatOutboxEventStatus.PUBLISHED : ChatOutboxEventStatus.PENDING;
        if (delivered) {
            publishedAt = now;
            lastError = null;
        } else {
            lastError = failureMessage;
        }
        updatedAt = now;
        return true;
    }

    public boolean markRead(Instant now) {
        if (isRead()) { return false; }
        status = ChatOutboxEventStatus.READ;
        readAt = now;
        updatedAt = now;
        return true;
    }

    public UUID getId() {
        return id;
    }
    public String getEventType() {
        return eventType;
    }
    public UUID getAggregateId() {
        return aggregateId;
    }
    public UUID getChatSessionId() {
        return chatSessionId;
    }
    public String getPayload() {
        return payload;
    }
    public ChatOutboxEventStatus getStatus() {
        return status;
    }
    public int getAttemptCount() {
        return attemptCount;
    }
    public Instant getAvailableAt() {
        return availableAt;
    }
    public Instant getPublishedAt() {
        return publishedAt;
    }
    public Instant getReadAt() {
        return readAt;
    }
    public String getLastError() {
        return lastError;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
