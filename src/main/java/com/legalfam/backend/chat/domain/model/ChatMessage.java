package com.legalfam.backend.chat.domain.model;

import com.legalfam.backend.chat.domain.exception.ChatApiError;
import com.legalfam.backend.chat.domain.exception.InvalidChatRequestException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ChatMessage {

    private UUID id;
    private UUID chatSessionId;
    private ChatMessageRole role;
    private String content;
    private String errorCode;
    private Integer rating;
    private String feedbackComment;
    private Instant feedbackSubmittedAt;
    private String confidenceStatus;
    private String confidenceReason;
    private List<String> nextSteps = List.of();
    private Boolean specialistSupportRecommended;
    private String citationSupportStatus;
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
        return systemMessage(chatSessionId, content, null, createdAt);
    }

    public static ChatMessage systemMessage(UUID chatSessionId, String content, String errorCode, Instant createdAt) {
        ChatMessage message = new ChatMessage();
        message.id = UUID.randomUUID();
        message.chatSessionId = chatSessionId;
        message.role = ChatMessageRole.SYSTEM;
        message.content = content;
        message.errorCode = normalizeBlank(errorCode);
        message.createdAt = createdAt;
        return message;
    }

    public static ChatMessage restore(
            UUID id,
            UUID chatSessionId,
            ChatMessageRole role,
            String content,
            String errorCode,
            Integer rating,
            String feedbackComment,
            Instant feedbackSubmittedAt,
            String confidenceStatus,
            String confidenceReason,
            List<String> nextSteps,
            Boolean specialistSupportRecommended,
            String citationSupportStatus,
            Instant createdAt
    ) {
        ChatMessage message = new ChatMessage();
        message.id = id;
        message.chatSessionId = chatSessionId;
        message.role = role;
        message.content = content;
        message.errorCode = normalizeBlank(errorCode);
        message.rating = rating;
        message.feedbackComment = feedbackComment;
        message.feedbackSubmittedAt = feedbackSubmittedAt;
        message.confidenceStatus = confidenceStatus;
        message.confidenceReason = confidenceReason;
        message.nextSteps = nextSteps == null ? List.of() : List.copyOf(nextSteps);
        message.specialistSupportRecommended = specialistSupportRecommended;
        message.citationSupportStatus = normalizeCitationSupportStatus(citationSupportStatus);
        message.createdAt = createdAt;
        return message;
    }

    public void applyAssistantMetadata(
            String confidenceStatus,
            String confidenceReason,
            List<String> nextSteps,
            Boolean specialistSupportRecommended,
            String citationSupportStatus
    ) {
        if (role != ChatMessageRole.ASSISTANT) {
            throw InvalidChatRequestException.of(ChatApiError.METADATA_ONLY_ASSISTANT);
        }
        this.confidenceStatus = normalizeBlank(confidenceStatus);
        this.confidenceReason = normalizeBlank(confidenceReason);
        this.nextSteps = nextSteps == null ? List.of() : List.copyOf(nextSteps);
        this.specialistSupportRecommended = specialistSupportRecommended;
        this.citationSupportStatus = normalizeCitationSupportStatus(citationSupportStatus);
    }

    public void submitFeedback(int rating, String comment, Instant submittedAt) {
        if (role != ChatMessageRole.ASSISTANT) {
            throw InvalidChatRequestException.of(ChatApiError.ONLY_ASSISTANT_MESSAGES_CAN_BE_RATED);
        }
        if (rating < 1 || rating > 5) {
            throw InvalidChatRequestException.of(ChatApiError.RATING_OUT_OF_RANGE);
        }
        this.rating = rating;
        this.feedbackComment = normalizeFeedbackComment(comment);
        this.feedbackSubmittedAt = submittedAt;
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

    public String getErrorCode() {
        return errorCode;
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

    public String getCitationSupportStatus() {
        return citationSupportStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private String normalizeFeedbackComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        String normalized = comment.trim();
        if (normalized.length() > 1000) {
            throw InvalidChatRequestException.of(ChatApiError.FEEDBACK_COMMENT_TOO_LONG);
        }
        return normalized;
    }

    private static String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeCitationSupportStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "GOOD", "WEAK", "NONE" -> normalized;
            default -> null;
        };
    }
}
