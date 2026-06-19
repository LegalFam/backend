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
    public UUID getSubscriptionId() {
        return subscriptionId;
    }
    public UUID getUserId() {
        return userId;
    }
    public UUID getChatMessageId() {
        return chatMessageId;
    }
    public TokenTransactionType getType() {
        return type;
    }
    public int getTokenDelta() {
        return tokenDelta;
    }
    public String getDescription() {
        return description;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
}
