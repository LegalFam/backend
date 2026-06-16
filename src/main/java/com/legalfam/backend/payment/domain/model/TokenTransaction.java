package com.legalfam.backend.payment.domain.model;

import java.time.Instant;
import java.util.UUID;

public class TokenTransaction {

    private UUID id;
    private UUID subscriptionId;
    private UUID userId;
    private UUID chatMessageId;
    private TokenTransactionType type;
    private int tokenDelta;
    private String description;
    private Instant createdAt;

    public static TokenTransaction create(
            UUID subscriptionId,
            UUID userId,
            UUID chatMessageId,
            TokenTransactionType type,
            int tokenDelta,
            String description,
            Instant createdAt
    ) {
        TokenTransaction transaction = new TokenTransaction();
        transaction.subscriptionId = subscriptionId;
        transaction.userId = userId;
        transaction.chatMessageId = chatMessageId;
        transaction.type = type;
        transaction.tokenDelta = tokenDelta;
        transaction.description = description;
        transaction.createdAt = createdAt;
        return transaction;
    }

    public static TokenTransaction restore(
            UUID id,
            UUID subscriptionId,
            UUID userId,
            UUID chatMessageId,
            TokenTransactionType type,
            int tokenDelta,
            String description,
            Instant createdAt
    ) {
        TokenTransaction transaction = create(subscriptionId, userId, chatMessageId, type, tokenDelta, description, createdAt);
        transaction.id = id;
        return transaction;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(UUID subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getChatMessageId() {
        return chatMessageId;
    }

    public void setChatMessageId(UUID chatMessageId) {
        this.chatMessageId = chatMessageId;
    }

    public TokenTransactionType getType() {
        return type;
    }

    public void setType(TokenTransactionType type) {
        this.type = type;
    }

    public int getTokenDelta() {
        return tokenDelta;
    }

    public void setTokenDelta(int tokenDelta) {
        this.tokenDelta = tokenDelta;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
