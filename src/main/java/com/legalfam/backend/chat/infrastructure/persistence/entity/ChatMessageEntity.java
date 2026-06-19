package com.legalfam.backend.chat.infrastructure.persistence.entity;

import com.legalfam.backend.chat.domain.model.ChatMessageRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "chat_message")
public class ChatMessageEntity {

    @Id
    private UUID id;

    @Column(name = "chat_session_id", nullable = false)
    private UUID chatSessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatMessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "error_code")
    private String errorCode;

    @Column(nullable = true)
    private Integer rating;

    @Column(name = "feedback_comment", columnDefinition = "TEXT")
    private String feedbackComment;

    @Column(name = "feedback_submitted_at")
    private Instant feedbackSubmittedAt;

    @Column(name = "confidence_status")
    private String confidenceStatus;

    @Column(name = "confidence_reason", columnDefinition = "TEXT")
    private String confidenceReason;

    @Column(name = "next_steps", columnDefinition = "TEXT")
    private String nextSteps;

    @Column(name = "specialist_support_recommended")
    private Boolean specialistSupportRecommended;

    @Column(name = "citation_support_status")
    private String citationSupportStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
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

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
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

    public String getNextSteps() {
        return nextSteps;
    }

    public void setNextSteps(String nextSteps) {
        this.nextSteps = nextSteps;
    }

    public Boolean getSpecialistSupportRecommended() {
        return specialistSupportRecommended;
    }

    public void setSpecialistSupportRecommended(Boolean specialistSupportRecommended) {
        this.specialistSupportRecommended = specialistSupportRecommended;
    }

    public String getCitationSupportStatus() {
        return citationSupportStatus;
    }

    public void setCitationSupportStatus(String citationSupportStatus) {
        this.citationSupportStatus = citationSupportStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
