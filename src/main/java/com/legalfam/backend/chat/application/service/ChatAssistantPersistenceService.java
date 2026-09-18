package com.legalfam.backend.chat.application.service;

import com.legalfam.backend.chat.application.dto.ChatAssistantErrorDispatch;
import com.legalfam.backend.chat.application.dto.ChatAssistantGatewayResponse;
import com.legalfam.backend.chat.application.dto.ChatAssistantMetadata;
import com.legalfam.backend.chat.application.dto.ChatAssistantMessageDispatch;
import com.legalfam.backend.chat.application.event.ChatAssistantDeliveryQueuedEvent;
import com.legalfam.backend.chat.application.port.out.IChatPersistencePort;
import com.legalfam.backend.chat.application.event.ChatAssistantMessageEvent;
import com.legalfam.backend.chat.application.event.ChatAssistantErrorEvent;
import com.legalfam.backend.chat.application.dto.ChatCitationResponse;
import com.legalfam.backend.chat.application.port.in.IChatAssistantPersistenceUseCase;
import com.legalfam.backend.chat.application.port.out.IChatOutboxPort;
import com.legalfam.backend.chat.application.port.out.IChatTokenPort;
import com.legalfam.backend.chat.domain.model.ChatCitation;
import com.legalfam.backend.chat.domain.model.ChatLanguage;
import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessing;
import com.legalfam.backend.chat.domain.model.ChatMessageRole;
import com.legalfam.backend.chat.domain.model.ChatSession;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatAssistantPersistenceService implements IChatAssistantPersistenceUseCase {

    private static final Logger log = LoggerFactory.getLogger(ChatAssistantPersistenceService.class);

    private final IChatPersistencePort IChatPersistencePort;
    private final IChatOutboxPort IChatOutboxPort;
    private final IChatTokenPort IChatTokenPort;

    public ChatAssistantPersistenceService(
            IChatPersistencePort IChatPersistencePort,
            IChatOutboxPort IChatOutboxPort,
            IChatTokenPort IChatTokenPort
    ) {
        this.IChatPersistencePort = IChatPersistencePort;
        this.IChatOutboxPort = IChatOutboxPort;
        this.IChatTokenPort = IChatTokenPort;
    }

    @Transactional
    @Override
    public boolean markUserMessageProcessing(UUID userMessageId) {
        ChatMessage userMessage = IChatPersistencePort.findMessageById(userMessageId).orElse(null);
        if (userMessage == null) {
            log.warn("Skipping processing start: chat message not found userMessageId={}", userMessageId);
            return false;
        }
        if (userMessage.getRole() != ChatMessageRole.USER) {
            log.warn("Skipping processing start: message is not a user message userMessageId={} role={}",
                    userMessageId, userMessage.getRole());
            return false;
        }

        ChatMessageProcessing processing = IChatPersistencePort.findMessageProcessingByUserMessageIdForUpdate(userMessageId)
                .orElseGet(() -> initializeProcessingRecord(userMessageId, Instant.now()));
        if (processing.isTerminal()) {
            return false;
        }
        if (processing.isProcessing()) {
            return true;
        }

        Instant now = Instant.now();
        processing.start(now);
        IChatPersistencePort.saveMessageProcessing(processing);
        return true;
    }

    @Transactional
    @Override
    public ChatAssistantMessageDispatch persistAssistantMessage(
            UUID chatSessionId,
            UUID userMessageId,
            ChatAssistantGatewayResponse response,
            ChatLanguage language
    ) {
        ChatSession chatSession = IChatPersistencePort.findSessionById(chatSessionId).orElse(null);
        if (chatSession == null) {
            log.warn("Skipping assistant response persistence: chat session not found chatSessionId={}", chatSessionId);
            return null;
        }

        ChatLanguage requestedLanguage = language == null ? ChatLanguage.ES : language;
        // El idioma con que se envio el turno es la preferencia de interfaz del usuario, y
        // puede no ser la lengua en que escribio. El flujo la lee del texto y esa lectura
        // manda; si no la devuelve (flujo anterior a la deteccion) se respeta la solicitada.
        String detected = response.languageDetected();
        ChatLanguage effectiveLanguage = detected == null || detected.isBlank()
                ? requestedLanguage
                : ChatLanguage.fromCode(detected);
        String assistantMessageText = response.message();
        List<ChatCitationResponse> citations = response.citations();
        ChatAssistantMetadata metadata = response.metadata();

        Instant now = Instant.now();
        ChatMessage assistantMessage = ChatMessage.assistantMessage(
                chatSession.getId(),
                assistantMessageText,
                response.messageLocalized(),
                effectiveLanguage,
                requestedLanguage,
                now
        );
        applyMetadata(assistantMessage, metadata);
        assistantMessage = IChatPersistencePort.saveMessage(assistantMessage);

        persistCitations(assistantMessage, citations);
        consumeTokensForAssistantResult(chatSession.getUserId(), userMessageId, metadata);
        markUserMessageCompleted(userMessageId, now, effectiveLanguage, response.userMessageTranslated());
        chatSession.recordActivity(now);
        IChatPersistencePort.saveSession(chatSession);

        ChatAssistantMessageEvent assistantMessageEvent = new ChatAssistantMessageEvent(
                chatSession.getId(),
                assistantMessage.getId(),
                assistantMessageText,
                assistantMessage.getLanguage().code(),
                assistantMessage.getLanguageRequested() == null
                        ? null
                        : assistantMessage.getLanguageRequested().code(),
                assistantMessage.getContentLocalized(),
                assistantMessage.getCreatedAt(),
                citations,
                assistantMessage.getConfidenceStatus(),
                assistantMessage.getConfidenceReason(),
                assistantMessage.getNextSteps(),
                assistantMessage.getNextStepsLocalized(),
                assistantMessage.getSpecialistSupportRecommended(),
                assistantMessage.getCitationSupportStatus(),
                "PENDING",
                true
        );
        IChatOutboxPort.enqueueAssistantDelivery(
                ChatAssistantDeliveryQueuedEvent.ofMessage(chatSession.getUserId(), assistantMessageEvent)
        );

        return new ChatAssistantMessageDispatch(
                chatSession.getUserId(),
                chatSession.getId(),
                assistantMessageEvent
        );
    }

    @Transactional
    @Override
    public ChatAssistantErrorDispatch persistAssistantFailure(
            UUID chatSessionId,
            UUID userMessageId,
            String errorCode,
            String errorMessage
    ) {
        ChatSession chatSession = IChatPersistencePort.findSessionById(chatSessionId).orElse(null);
        if (chatSession == null) {
            log.warn("Skipping assistant failure persistence: chat session not found chatSessionId={}", chatSessionId);
            return null;
        }

        Instant now = Instant.now();
        if (!markUserMessageFailed(userMessageId, errorCode, errorMessage, now)) {
            log.debug("Skipping assistant failure: user message already finished userMessageId={}", userMessageId);
            return null;
        }
        ChatMessage failureMessage = ChatMessage.systemMessage(chatSession.getId(), errorMessage, errorCode, now);
        failureMessage = IChatPersistencePort.saveMessage(failureMessage);
        chatSession.recordActivity(now);
        IChatPersistencePort.saveSession(chatSession);

        ChatAssistantErrorEvent errorEvent = new ChatAssistantErrorEvent(
                chatSession.getId(),
                failureMessage.getId(),
                errorCode,
                errorMessage,
                failureMessage.getCreatedAt(),
                "PENDING"
        );
        IChatOutboxPort.enqueueAssistantDelivery(
                ChatAssistantDeliveryQueuedEvent.ofError(chatSession.getUserId(), errorEvent)
        );
        return new ChatAssistantErrorDispatch(chatSession.getUserId(), chatSession.getId(), errorEvent);
    }

    private void persistCitations(ChatMessage assistantMessage, List<ChatCitationResponse> citations) {
        List<ChatCitation> chatCitations = citations.stream()
                .map(citation -> ChatCitation.create(
                    assistantMessage.getId(),
                    defaultString(citation.sourceTitle()),
                    defaultString(citation.sourceSnippet()),
                    citation.sourceSnippetLocalized(),
                    citation.sourceOriginalSnippet(),
                    defaultString(citation.sourceUrl()),
                    citation.sourceLocator(),
                    citation.sourceBreadcrumb(),
                    citation.sourceLocatorKind()
                ))
                .toList();

        IChatPersistencePort.saveCitations(chatCitations);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private void applyMetadata(ChatMessage assistantMessage, ChatAssistantMetadata metadata) {
        ChatAssistantMetadata safeMetadata = metadata == null ? ChatAssistantMetadata.empty() : metadata;
        assistantMessage.applyAssistantMetadata(
                safeMetadata.confidenceStatus(),
                safeMetadata.confidenceReason(),
                safeMetadata.nextSteps(),
                safeMetadata.nextStepsLocalized(),
                safeMetadata.specialistSupportRecommended(),
                safeMetadata.citationSupportStatus()
        );
    }

    private void consumeTokensForAssistantResult(UUID userId, UUID userMessageId, ChatAssistantMetadata metadata) {
        ChatAssistantMetadata safeMetadata = metadata == null ? ChatAssistantMetadata.empty() : metadata;
        int agentTokenCost = safeMetadata.agentTokenCost() == null ? 1 : safeMetadata.agentTokenCost();
        IChatTokenPort.consumeChatTokensForAssistantResult(userId, userMessageId, agentTokenCost);
    }

    private void markUserMessageCompleted(
            UUID userMessageId,
            Instant now,
            ChatLanguage detectedLanguage,
            String userMessageTranslated
    ) {
        ChatMessage userMessage = IChatPersistencePort.findMessageById(userMessageId).orElse(null);
        if (userMessage == null || userMessage.getRole() != ChatMessageRole.USER) {
            return;
        }
        // El mensaje se guardo con el idioma de la interfaz y antes de que existiera su
        // traduccion. Ahora que el flujo leyo la lengua real y devolvio el espanol, `content`
        // pasa a ser el espanol y el original queda en `content_localized`, para que el
        // proximo turno mande historial en un solo idioma. Si la traduccion no llego, el
        // idioma se corrige igual y el turno sigue mostrandose en la lengua original: peor
        // historial, pero nunca un mensaje perdido.
        userMessage.applyDetectedLanguage(detectedLanguage, userMessageTranslated);
        IChatPersistencePort.saveMessage(userMessage);
        ChatMessageProcessing processing = IChatPersistencePort.findMessageProcessingByUserMessageIdForUpdate(userMessageId)
                .orElseGet(() -> initializeProcessingRecord(userMessageId, now));
        if (processing.complete(now)) {
            IChatPersistencePort.saveMessageProcessing(processing);
        }
    }

    private boolean markUserMessageFailed(UUID userMessageId, String errorCode, String errorMessage, Instant now) {
        ChatMessage userMessage = IChatPersistencePort.findMessageById(userMessageId).orElse(null);
        if (userMessage == null || userMessage.getRole() != ChatMessageRole.USER) {
            return false;
        }
        ChatMessageProcessing processing = IChatPersistencePort.findMessageProcessingByUserMessageIdForUpdate(userMessageId)
                .orElseGet(() -> initializeProcessingRecord(userMessageId, now));
        if (!processing.fail(errorCode, errorMessage, now)) {
            return false;
        }
        IChatPersistencePort.saveMessageProcessing(processing);
        return true;
    }

    private ChatMessageProcessing initializeProcessingRecord(UUID userMessageId, Instant now) {
        return ChatMessageProcessing.queued(userMessageId, now);
    }

}
