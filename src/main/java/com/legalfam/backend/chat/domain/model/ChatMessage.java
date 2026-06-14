package com.legalfam.backend.chat.domain.model;

import com.legalfam.backend.chat.domain.exception.InvalidChatRequestException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ChatMessage {

    private UUID id;
    private UUID chatSessionId;
    private ChatMessageRole role;
    private String content;
    private Integer rating;
    private String feedbackComment;
    private Instant feedbackSubmittedAt;
    private String confidenceStatus;
    private String confidenceReason;
    private List<String> nextSteps = List.of();
    private Boolean specialistSupportRecommended;
    private Instant createdAt;

    private ChatMessage() {
    }

    public static ChatMessage userMessage(UUID chatSessionId, String content, Instant createdAt) {
        ChatMessage message = new ChatMessage();
        message.id = UUID.randomUUID();
        message.chatSessionId = chatSessionId;
        message.role = ChatMessageRole.USER;
        message.content = content;
        message.createdAt = createdAt;
        return message;
    }

    public static ChatMessage assistantMessage(UUID chatSessionId, String content, Instant createdAt) {
        ChatMessage message = new ChatMessage();
        message.id = UUID.randomUUID();
        message.chatSessionId = chatSessionId;
        message.role = ChatMessageRole.ASSISTANT;
        message.content = content;
        message.createdAt = createdAt;
        return message;
    }

    public static ChatMessage systemMessage(UUID chatSessionId, String content, Instant createdAt) {
        ChatMessage message = new ChatMessage();
        message.id = UUID.randomUUID();
        message.chatSessionId = chatSessionId;
        message.role = ChatMessageRole.SYSTEM;
        message.content = content;
        message.createdAt = createdAt;
        return message;
    }

    public static ChatMessage rehydrate(
            UUID id,
            UUID chatSessionId,
            ChatMessageRole role,
            String content,
            Integer rating,
            String feedbackComment,
            Instant feedbackSubmittedAt,
            String confidenceStatus,
            String confidenceReason,
            List<String> nextSteps,
            Boolean specialistSupportRecommended,
            Instant createdAt
    ) {
        ChatMessage message = new ChatMessage();
        message.id = id;
        message.chatSessionId = chatSessionId;
        message.role = role;
        message.content = content;
        message.rating = rating;
        message.feedbackComment = feedbackComment;
        message.feedbackSubmittedAt = feedbackSubmittedAt;
        message.confidenceStatus = confidenceStatus;
        message.confidenceReason = confidenceReason;
        message.nextSteps = nextSteps == null ? List.of() : List.copyOf(nextSteps);
        message.specialistSupportRecommended = specialistSupportRecommended;
        message.createdAt = createdAt;
        return message;
    }

    public UUID getId() {
        return id;
    }

    public UUID getChatSessionId() {
        return chatSessionId;
    }

    public ChatMessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public Integer getRating() {
        return rating;
    }

    public String getFeedbackComment() {
        return feedbackComment;
    }

    public Instant getFeedbackSubmittedAt() {
        return feedbackSubmittedAt;
    }

    public String getConfidenceStatus() {
        return confidenceStatus;
    }

    public String getConfidenceReason() {
        return confidenceReason;
    }

    public List<String> getNextSteps() {
        return nextSteps;
    }

    public Boolean getSpecialistSupportRecommended() {
        return specialistSupportRecommended;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void applyAssistantMetadata(
            String confidenceStatus,
            String confidenceReason,
            List<String> nextSteps,
            Boolean specialistSupportRecommended
    ) {
        if (role != ChatMessageRole.ASSISTANT) {
            throw new InvalidChatRequestException("Metadata can only be applied to assistant messages");
        }
        this.confidenceStatus = normalizeBlank(confidenceStatus);
        this.confidenceReason = normalizeBlank(confidenceReason);
        this.nextSteps = nextSteps == null ? List.of() : List.copyOf(nextSteps);
        this.specialistSupportRecommended = specialistSupportRecommended;
    }

    public void submitFeedback(int rating, String comment, Instant submittedAt) {
        if (role != ChatMessageRole.ASSISTANT) {
            throw new InvalidChatRequestException("Only assistant messages can be rated");
        }
        if (rating < 1 || rating > 5) {
            throw new InvalidChatRequestException("Rating must be between 1 and 5");
        }
        this.rating = rating;
        this.feedbackComment = normalizeFeedbackComment(comment);
        this.feedbackSubmittedAt = submittedAt;
    }

    private String normalizeFeedbackComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        String normalized = comment.trim();
        if (normalized.length() > 1000) {
            throw new InvalidChatRequestException("Feedback comment must be at most 1000 characters");
        }
        return normalized;
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
