package com.legalfam.backend.chat.domain.model;

import java.time.Instant;
import java.util.UUID;

public class ChatMessageProcessing {

    private UUID id;
    private UUID userMessageId;
    private ChatMessageProcessingStatus status;
    private String errorCode;
    private String errorMessage;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public static ChatMessageProcessing queued(UUID userMessageId, Instant now) {
        ChatMessageProcessing processing = new ChatMessageProcessing();
        processing.userMessageId = userMessageId;
        processing.status = ChatMessageProcessingStatus.QUEUED;
        processing.createdAt = now;
        processing.updatedAt = now;
        return processing;
    }

    public static ChatMessageProcessing restore(
            UUID id,
            UUID userMessageId,
            ChatMessageProcessingStatus status,
            String errorCode,
            String errorMessage,
            Instant startedAt,
            Instant finishedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        ChatMessageProcessing processing = new ChatMessageProcessing();
        processing.id = id;
        processing.userMessageId = userMessageId;
        processing.status = status;
        processing.errorCode = errorCode;
        processing.errorMessage = errorMessage;
        processing.startedAt = startedAt;
        processing.finishedAt = finishedAt;
        processing.createdAt = createdAt;
        processing.updatedAt = updatedAt;
        return processing;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserMessageId() {
        return userMessageId;
    }

    public void setUserMessageId(UUID userMessageId) {
        this.userMessageId = userMessageId;
    }

    public ChatMessageProcessingStatus getStatus() {
        return status;
    }

    public void setStatus(ChatMessageProcessingStatus status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
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

    public boolean isTerminal() {
        return status == ChatMessageProcessingStatus.COMPLETED
                || status == ChatMessageProcessingStatus.FAILED
                || status == ChatMessageProcessingStatus.EXPIRED;
    }

    public boolean isProcessing() {
        return status == ChatMessageProcessingStatus.PROCESSING;
    }

    public boolean start(Instant now) {
        if (isTerminal()) {
            return false;
        }
        if (isProcessing()) {
            return true;
        }
        status = ChatMessageProcessingStatus.PROCESSING;
        startedAt = now;
        finishedAt = null;
        errorCode = null;
        errorMessage = null;
        updatedAt = now;
        return true;
    }

    public boolean complete(Instant now) {
        if (isTerminal()) {
            return false;
        }
        status = ChatMessageProcessingStatus.COMPLETED;
        ensureStartedAt(now);
        finishedAt = now;
        errorCode = null;
        errorMessage = null;
        updatedAt = now;
        return true;
    }

    public boolean fail(String errorCode, String errorMessage, Instant now) {
        if (isTerminal()) {
            return false;
        }
        status = ChatMessageProcessingStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        ensureStartedAt(now);
        finishedAt = now;
        updatedAt = now;
        return true;
    }

    public boolean expire(String errorCode, String errorMessage, Instant now) {
        if (isTerminal()) {
            return false;
        }
        status = ChatMessageProcessingStatus.EXPIRED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        ensureStartedAt(now);
        finishedAt = now;
        updatedAt = now;
        return true;
    }

    private void ensureStartedAt(Instant now) {
        if (startedAt == null) {
            startedAt = now;
        }
    }
}
