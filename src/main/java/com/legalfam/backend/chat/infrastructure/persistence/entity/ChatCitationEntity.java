package com.legalfam.backend.chat.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "citations")
public class ChatCitationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "chat_message_id", nullable = false)
    private UUID chatMessageId;

    @Column(name = "source_title", nullable = false, columnDefinition = "TEXT")
    private String sourceTitle;

    @Column(name = "source_snippet", nullable = false, columnDefinition = "TEXT")
    private String sourceSnippet;

    @Column(name = "source_original_snippet", columnDefinition = "TEXT")
    private String sourceOriginalSnippet;

    @Column(name = "source_url", nullable = false, columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "source_locator", columnDefinition = "TEXT")
    private String sourceLocator;

    @Column(name = "source_breadcrumb", columnDefinition = "TEXT")
    private String sourceBreadcrumb;

    @Column(name = "source_locator_kind", length = 32)
    private String sourceLocatorKind;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getChatMessageId() {
        return chatMessageId;
    }

    public void setChatMessageId(UUID chatMessageId) {
        this.chatMessageId = chatMessageId;
    }

    public String getSourceTitle() {
        return sourceTitle;
    }

    public void setSourceTitle(String sourceTitle) {
        this.sourceTitle = sourceTitle;
    }

    public String getSourceSnippet() {
        return sourceSnippet;
    }

    public void setSourceSnippet(String sourceSnippet) {
        this.sourceSnippet = sourceSnippet;
    }

    public String getSourceOriginalSnippet() {
        return sourceOriginalSnippet;
    }

    public void setSourceOriginalSnippet(String sourceOriginalSnippet) {
        this.sourceOriginalSnippet = sourceOriginalSnippet;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getSourceLocator() {
        return sourceLocator;
    }

    public void setSourceLocator(String sourceLocator) {
        this.sourceLocator = sourceLocator;
    }

    public String getSourceBreadcrumb() {
        return sourceBreadcrumb;
    }

    public void setSourceBreadcrumb(String sourceBreadcrumb) {
        this.sourceBreadcrumb = sourceBreadcrumb;
    }

    public String getSourceLocatorKind() {
        return sourceLocatorKind;
    }

    public void setSourceLocatorKind(String sourceLocatorKind) {
        this.sourceLocatorKind = sourceLocatorKind;
    }
}
