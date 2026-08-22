package com.legalfam.backend.chat.domain.model;

import java.util.UUID;

public class ChatCitation {

    private UUID id;
    private UUID chatMessageId;
    private String sourceTitle;
    private String sourceSnippet;
    private String sourceUrl;
    private String sourceLocator;
    private String sourceBreadcrumb;
    private String sourceLocatorKind;

    private ChatCitation() {}

    public static ChatCitation create(
            UUID chatMessageId,
            String sourceTitle,
            String sourceSnippet,
            String sourceUrl,
            String sourceLocator,
            String sourceBreadcrumb,
            String sourceLocatorKind
    ) {
        ChatCitation citation = new ChatCitation();
        citation.chatMessageId = chatMessageId;
        citation.sourceTitle = sourceTitle;
        citation.sourceSnippet = sourceSnippet;
        citation.sourceUrl = sourceUrl;
        citation.sourceLocator = sourceLocator;
        citation.sourceBreadcrumb = sourceBreadcrumb;
        citation.sourceLocatorKind = sourceLocatorKind;
        return citation;
    }

    public static ChatCitation restore(
            UUID id,
            UUID chatMessageId,
            String sourceTitle,
            String sourceSnippet,
            String sourceUrl,
            String sourceLocator,
            String sourceBreadcrumb,
            String sourceLocatorKind
    ) {
        ChatCitation citation = create(
                chatMessageId,
                sourceTitle,
                sourceSnippet,
                sourceUrl,
                sourceLocator,
                sourceBreadcrumb,
                sourceLocatorKind
        );
        citation.id = id;
        return citation;
    }

    public UUID getId() {
        return id;
    }

    public UUID getChatMessageId() {
        return chatMessageId;
    }

    public String getSourceTitle() {
        return sourceTitle;
    }

    public String getSourceSnippet() {
        return sourceSnippet;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getSourceLocator() {
        return sourceLocator;
    }

    public String getSourceBreadcrumb() {
        return sourceBreadcrumb;
    }

    public String getSourceLocatorKind() {
        return sourceLocatorKind;
    }
}
