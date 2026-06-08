package com.legalfam.backend.chat.domain.model;

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
    private List<String> clarifyingQuestions = List.of();
    private List<String> preliminaryActions = List.of();
    private Boolean specialistSupportRecommended;
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getChatSessionId() {
        return chatSessionId;
    }

    public void setChatSessionId(UUID chatSessionId) {
        this.chatSessionId = chatSessionId;
    }

    public ChatMessageRole getRole() {
        return role;
    }

    public void setRole(ChatMessageRole role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getFeedbackComment() {
        return feedbackComment;
    }

    public void setFeedbackComment(String feedbackComment) {
        this.feedbackComment = feedbackComment;
    }

    public Instant getFeedbackSubmittedAt() {
        return feedbackSubmittedAt;
    }

    public void setFeedbackSubmittedAt(Instant feedbackSubmittedAt) {
        this.feedbackSubmittedAt = feedbackSubmittedAt;
    }

    public String getConfidenceStatus() {
        return confidenceStatus;
    }

    public void setConfidenceStatus(String confidenceStatus) {
        this.confidenceStatus = confidenceStatus;
    }

    public String getConfidenceReason() {
        return confidenceReason;
    }

    public void setConfidenceReason(String confidenceReason) {
        this.confidenceReason = confidenceReason;
    }

    public List<String> getClarifyingQuestions() {
        return clarifyingQuestions;
    }

    public void setClarifyingQuestions(List<String> clarifyingQuestions) {
        this.clarifyingQuestions = clarifyingQuestions == null ? List.of() : clarifyingQuestions;
    }

    public List<String> getPreliminaryActions() {
        return preliminaryActions;
    }

    public void setPreliminaryActions(List<String> preliminaryActions) {
        this.preliminaryActions = preliminaryActions == null ? List.of() : preliminaryActions;
    }

    public Boolean getSpecialistSupportRecommended() {
        return specialistSupportRecommended;
    }

    public void setSpecialistSupportRecommended(Boolean specialistSupportRecommended) {
        this.specialistSupportRecommended = specialistSupportRecommended;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
