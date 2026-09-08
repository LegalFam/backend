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
    // Siempre el espanol canonico. De aca sale el historial que viaja al flujo n8n, y por
    // eso no cambia aunque el usuario lea en quechua o aymara.
    private String content;
    // El idioma EFECTIVO del turno: el que el flujo leyo del texto, no el que pidio la
    // interfaz. Es el que decide en que lengua se muestra el mensaje.
    private ChatLanguage language = ChatLanguage.ES;
    // El que si habia pedido la interfaz, y solo cuando difiere del efectivo. Null es el
    // caso normal. Se guarda porque el idioma de la interfaz cambia y el del mensaje no:
    // sin el, al recargar el historial se perderia la explicacion de por que ese turno
    // quedo en otra lengua.
    private ChatLanguage languageRequested;
    // El texto en la lengua del usuario. Null cuando la conversacion es en espanol.
    private String contentLocalized;
    private String errorCode;
    private Integer rating;
    private String feedbackComment;
    private Instant feedbackSubmittedAt;
    private String confidenceStatus;
    private String confidenceReason;
    private List<String> nextSteps = List.of();
    private List<String> nextStepsLocalized = List.of();
    private Boolean specialistSupportRecommended;
    private String citationSupportStatus;
    private Instant createdAt;

    private ChatMessage() {
    }

    public static ChatMessage userMessage(UUID chatSessionId, String content, Instant createdAt) {
        return userMessage(chatSessionId, content, ChatLanguage.ES, createdAt);
    }

    /**
     * El mensaje del usuario se guarda antes de que exista su traduccion, asi que cuando el
     * idioma no es espanol el texto original ocupa las dos ranuras: se muestra tal cual y
     * queda como {@code content} provisional. La traduccion llega despues, por
     * {@link #applyTranslatedContent(String)}, cuando el flujo n8n la devuelve.
     */
    public static ChatMessage userMessage(UUID chatSessionId, String content, ChatLanguage language, Instant createdAt) {
        ChatMessage message = new ChatMessage();
        message.id = UUID.randomUUID();
        message.chatSessionId = chatSessionId;
        message.role = ChatMessageRole.USER;
        message.content = content;
        message.language = language == null ? ChatLanguage.ES : language;
        message.contentLocalized = message.language.isSpanish() ? null : content;
        message.createdAt = createdAt;
        return message;
    }

    public static ChatMessage assistantMessage(UUID chatSessionId, String content, Instant createdAt) {
        return assistantMessage(chatSessionId, content, null, ChatLanguage.ES, createdAt);
    }

    public static ChatMessage assistantMessage(
            UUID chatSessionId,
            String content,
            String contentLocalized,
            ChatLanguage language,
            Instant createdAt
    ) {
        return assistantMessage(chatSessionId, content, contentLocalized, language, language, createdAt);
    }

    /**
     * La respuesta se redacta en el idioma que el flujo leyo del mensaje ({@code language}),
     * que no siempre es el que pidio la interfaz ({@code languageRequested}). Cuando
     * coinciden no hay nada que explicar y el solicitado queda en null.
     */
    public static ChatMessage assistantMessage(
            UUID chatSessionId,
            String content,
            String contentLocalized,
            ChatLanguage language,
            ChatLanguage languageRequested,
            Instant createdAt
    ) {
        ChatMessage message = new ChatMessage();
        message.id = UUID.randomUUID();
        message.chatSessionId = chatSessionId;
        message.role = ChatMessageRole.ASSISTANT;
        message.content = content;
        message.language = language == null ? ChatLanguage.ES : language;
        message.languageRequested = mismatchOrNull(message.language, languageRequested);
        message.contentLocalized = message.language.isSpanish() ? null : normalizeBlank(contentLocalized);
        message.createdAt = createdAt;
        return message;
    }

    private static ChatLanguage mismatchOrNull(ChatLanguage effective, ChatLanguage requested) {
        return requested == null || requested == effective ? null : requested;
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
            ChatLanguage language,
            ChatLanguage languageRequested,
            String contentLocalized,
            String errorCode,
            Integer rating,
            String feedbackComment,
            Instant feedbackSubmittedAt,
            String confidenceStatus,
            String confidenceReason,
            List<String> nextSteps,
            List<String> nextStepsLocalized,
            Boolean specialistSupportRecommended,
            String citationSupportStatus,
            Instant createdAt
    ) {
        ChatMessage message = new ChatMessage();
        message.id = id;
        message.chatSessionId = chatSessionId;
        message.role = role;
        message.content = content;
        message.language = language == null ? ChatLanguage.ES : language;
        message.languageRequested = mismatchOrNull(message.language, languageRequested);
        message.contentLocalized = normalizeBlank(contentLocalized);
        message.errorCode = normalizeBlank(errorCode);
        message.rating = rating;
        message.feedbackComment = feedbackComment;
        message.feedbackSubmittedAt = feedbackSubmittedAt;
        message.confidenceStatus = confidenceStatus;
        message.confidenceReason = confidenceReason;
        message.nextSteps = nextSteps == null ? List.of() : List.copyOf(nextSteps);
        message.nextStepsLocalized = nextStepsLocalized == null ? List.of() : List.copyOf(nextStepsLocalized);
        message.specialistSupportRecommended = specialistSupportRecommended;
        message.citationSupportStatus = normalizeCitationSupportStatus(citationSupportStatus);
        message.createdAt = createdAt;
        return message;
    }

    /**
     * Fija el espanol canonico de un mensaje del usuario escrito en otra lengua. Se llama
     * cuando el flujo n8n devuelve la traduccion, de modo que el historial que reciben los
     * agentes quede integramente en espanol y no mezclado.
     */
    /**
     * Corrige el idioma de un mensaje del usuario con el que el flujo leyo del texto y, de
     * paso, fija su espanol canonico. Se guardo con el idioma de la interfaz, que es la
     * unica pista disponible antes de la respuesta y puede estar equivocada.
     *
     * <p>Si el texto ya estaba en espanol, no hay traduccion ni version localizada que
     * conmutar: {@code content_localized} vuelve a quedar vacio.
     */
    public void applyDetectedLanguage(ChatLanguage detected, String translatedContent) {
        if (role != ChatMessageRole.USER || detected == null) {
            return;
        }
        ChatLanguage requested = language;
        language = detected;
        languageRequested = mismatchOrNull(language, requested);
        if (language.isSpanish()) {
            contentLocalized = null;
            return;
        }
        applyTranslatedContent(translatedContent);
    }

    public void applyTranslatedContent(String translatedContent) {
        if (role != ChatMessageRole.USER || language.isSpanish()) {
            return;
        }
        String normalized = normalizeBlank(translatedContent);
        if (normalized == null) {
            return;
        }
        if (contentLocalized == null) {
            contentLocalized = content;
        }
        content = normalized;
    }

    public void applyAssistantMetadata(
            String confidenceStatus,
            String confidenceReason,
            List<String> nextSteps,
            List<String> nextStepsLocalized,
            Boolean specialistSupportRecommended,
            String citationSupportStatus
    ) {
        if (role != ChatMessageRole.ASSISTANT) {
            throw InvalidChatRequestException.of(ChatApiError.METADATA_ONLY_ASSISTANT);
        }
        this.confidenceStatus = normalizeBlank(confidenceStatus);
        this.confidenceReason = normalizeBlank(confidenceReason);
        this.nextSteps = nextSteps == null ? List.of() : List.copyOf(nextSteps);
        this.nextStepsLocalized = nextStepsLocalized == null || language.isSpanish()
                ? List.of()
                : List.copyOf(nextStepsLocalized);
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

    /** Null cuando el idioma efectivo es el que se habia pedido, que es el caso normal. */
    public ChatLanguage getLanguageRequested() {
        return languageRequested;
    }

    public ChatLanguage getLanguage() {
        return language;
    }

    public String getContentLocalized() {
        return contentLocalized;
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

    public List<String> getNextStepsLocalized() {
        return nextStepsLocalized;
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
