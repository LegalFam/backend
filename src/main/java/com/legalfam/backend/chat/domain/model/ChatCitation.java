package com.legalfam.backend.chat.domain.model;

import java.util.UUID;

public class ChatCitation {

    private UUID id;
    private UUID chatMessageId;
    private String sourceTitle;
    private String sourceSnippet;
    // El resumen traducido a la lengua del usuario. El pasaje literal nunca se traduce: es
    // lo que permite contrastar la cita contra la norma.
    private String sourceSnippetLocalized;
    private String sourceOriginalSnippet;
    private String sourceUrl;
    private String sourceLocator;
    private String sourceBreadcrumb;
    private String sourceLocatorKind;

    private ChatCitation() {}

    public static ChatCitation create(
            UUID chatMessageId,
            String sourceTitle,
            String sourceSnippet,
            String sourceSnippetLocalized,
            String sourceOriginalSnippet,
            String sourceUrl,
            String sourceLocator,
            String sourceBreadcrumb,
            String sourceLocatorKind
    ) {
        ChatCitation citation = new ChatCitation();
        citation.chatMessageId = chatMessageId;
        citation.sourceTitle = sourceTitle;
        citation.sourceSnippet = sourceSnippet;
        citation.sourceSnippetLocalized = sourceSnippetLocalized;
        citation.sourceOriginalSnippet = sourceOriginalSnippet;
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
            String sourceSnippetLocalized,
            String sourceOriginalSnippet,
            String sourceUrl,
            String sourceLocator,
            String sourceBreadcrumb,
            String sourceLocatorKind
    ) {
        ChatCitation citation = create(
                chatMessageId,
                sourceTitle,
                sourceSnippet,
                sourceSnippetLocalized,
                sourceOriginalSnippet,
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

    public String getSourceSnippetLocalized() {
        return sourceSnippetLocalized;
    }

    public String getSourceOriginalSnippet() {
        return sourceOriginalSnippet;
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
