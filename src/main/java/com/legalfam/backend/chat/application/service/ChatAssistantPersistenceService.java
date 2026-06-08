package com.legalfam.backend.chat.application.service;

import com.legalfam.backend.chat.application.dto.ChatAssistantErrorDispatch;
import com.legalfam.backend.chat.application.dto.ChatAssistantMetadata;
import com.legalfam.backend.chat.application.dto.ChatAssistantMessageDispatch;
import com.legalfam.backend.chat.application.event.ChatAssistantDeliveryQueuedEvent;
import com.legalfam.backend.chat.application.port.out.ChatPersistencePort;
import com.legalfam.backend.chat.application.event.ChatAssistantMessageEvent;
import com.legalfam.backend.chat.application.event.ChatAssistantErrorEvent;
import com.legalfam.backend.chat.application.dto.ChatCitationResponse;
import com.legalfam.backend.chat.application.port.in.ChatAssistantPersistenceUseCase;
import com.legalfam.backend.chat.application.port.out.ChatOutboxPort;
import com.legalfam.backend.chat.domain.model.ChatCitation;
import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessing;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessingStatus;
import com.legalfam.backend.chat.domain.model.ChatMessageRole;
import com.legalfam.backend.chat.domain.model.ChatSession;
import com.legalfam.backend.payment.application.port.in.PaymentTokenUseCase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatAssistantPersistenceService implements ChatAssistantPersistenceUseCase {

    private static final Logger log = LoggerFactory.getLogger(ChatAssistantPersistenceService.class);

    private final ChatPersistencePort chatPersistencePort;
    private final ChatOutboxPort chatOutboxPort;
    private final PaymentTokenUseCase paymentTokenUseCase;

    public ChatAssistantPersistenceService(
            ChatPersistencePort chatPersistencePort,
            ChatOutboxPort chatOutboxPort,
            PaymentTokenUseCase paymentTokenUseCase
    ) {
        this.chatPersistencePort = chatPersistencePort;
        this.chatOutboxPort = chatOutboxPort;
        this.paymentTokenUseCase = paymentTokenUseCase;
    }

    @Transactional
    @Override
    public boolean markUserMessageProcessing(UUID userMessageId) {
        ChatMessage userMessage = chatPersistencePort.findMessageById(userMessageId).orElse(null);
        if (userMessage == null) {
            log.warn("Skipping processing start: chat message not found userMessageId={}", userMessageId);
            return false;
        }
        if (userMessage.getRole() != ChatMessageRole.USER) {
            log.warn("Skipping processing start: message is not a user message userMessageId={} role={}",
                    userMessageId, userMessage.getRole());
            return false;
        }

        ChatMessageProcessing processing = chatPersistencePort.findMessageProcessingByUserMessageIdForUpdate(userMessageId)
                .orElseGet(() -> initializeProcessingRecord(userMessageId, Instant.now()));
        if (processing.getStatus() == ChatMessageProcessingStatus.COMPLETED
                || processing.getStatus() == ChatMessageProcessingStatus.FAILED
                || processing.getStatus() == ChatMessageProcessingStatus.EXPIRED) {
            return false;
        }
        if (processing.getStatus() == ChatMessageProcessingStatus.PROCESSING) {
            return true;
        }

        Instant now = Instant.now();
        processing.setStatus(ChatMessageProcessingStatus.PROCESSING);
        processing.setStartedAt(now);
        processing.setFinishedAt(null);
        processing.setErrorCode(null);
        processing.setErrorMessage(null);
        processing.setUpdatedAt(now);
        chatPersistencePort.saveMessageProcessing(processing);
        return true;
    }

    @Transactional
    @Override
    public ChatAssistantMessageDispatch persistAssistantMessage(
            UUID chatSessionId,
            UUID userMessageId,
            String assistantMessageText,
            List<ChatCitationResponse> citations,
            ChatAssistantMetadata metadata
    ) {
        ChatSession chatSession = chatPersistencePort.findSessionById(chatSessionId).orElse(null);
        if (chatSession == null) {
            log.warn("Skipping assistant response persistence: chat session not found chatSessionId={}", chatSessionId);
            return null;
        }

        Instant now = Instant.now();
        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setChatSessionId(chatSession.getId());
        assistantMessage.setRole(ChatMessageRole.ASSISTANT);
        assistantMessage.setContent(assistantMessageText);
        applyMetadata(assistantMessage, metadata);
        assistantMessage.setCreatedAt(now);
        assistantMessage = chatPersistencePort.saveMessage(assistantMessage);

        persistCitations(assistantMessage, citations);
        markUserMessageCompleted(userMessageId, now);

        chatSession.setUpdatedAt(now);
        chatPersistencePort.saveSession(chatSession);

        ChatAssistantMessageEvent assistantMessageEvent = new ChatAssistantMessageEvent(
                chatSession.getId(),
                assistantMessage.getId(),
                assistantMessageText,
                assistantMessage.getCreatedAt(),
                citations,
                assistantMessage.getConfidenceStatus(),
                assistantMessage.getConfidenceReason(),
                assistantMessage.getClarifyingQuestions(),
                assistantMessage.getPreliminaryActions(),
                assistantMessage.getSpecialistSupportRecommended(),
                "PENDING",
                true
        );
        chatOutboxPort.enqueueAssistantDelivery(new ChatAssistantDeliveryQueuedEvent(
                chatSession.getUserId(),
                chatSession.getId(),
                assistantMessage.getId(),
                assistantMessageEvent
        ));

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
        ChatSession chatSession = chatPersistencePort.findSessionById(chatSessionId).orElse(null);
        if (chatSession == null) {
            log.warn("Skipping assistant failure persistence: chat session not found chatSessionId={}", chatSessionId);
            return null;
        }

        Instant now = Instant.now();
        ChatMessage failureMessage = new ChatMessage();
        failureMessage.setChatSessionId(chatSession.getId());
        failureMessage.setRole(ChatMessageRole.SYSTEM);
        failureMessage.setContent(errorMessage);
        failureMessage.setCreatedAt(now);
        failureMessage = chatPersistencePort.saveMessage(failureMessage);
        markUserMessageFailed(userMessageId, errorCode, errorMessage, now);

        chatSession.setUpdatedAt(now);
        chatPersistencePort.saveSession(chatSession);
        paymentTokenUseCase.refundChatToken(userMessageId);

        return new ChatAssistantErrorDispatch(
                chatSession.getUserId(),
                chatSession.getId(),
                new ChatAssistantErrorEvent(
                        chatSession.getId(),
                        failureMessage.getId(),
                        errorCode,
                        errorMessage,
                        failureMessage.getCreatedAt()
                )
        );
    }

    @Transactional
    @Override
    public void expireUserMessage(UUID userMessageId, String errorCode, String errorMessage) {
        Instant now = Instant.now();
        ChatMessage userMessage = chatPersistencePort.findMessageById(userMessageId).orElse(null);
        if (userMessage == null || userMessage.getRole() != ChatMessageRole.USER) {
            return;
        }
        ChatMessageProcessing processing = chatPersistencePort.findMessageProcessingByUserMessageIdForUpdate(userMessageId)
                .orElseGet(() -> initializeProcessingRecord(userMessageId, now));
        if (processing.getStatus() == ChatMessageProcessingStatus.COMPLETED
                || processing.getStatus() == ChatMessageProcessingStatus.FAILED
                || processing.getStatus() == ChatMessageProcessingStatus.EXPIRED) {
            return;
        }

        processing.setStatus(ChatMessageProcessingStatus.EXPIRED);
        processing.setErrorCode(errorCode);
        processing.setErrorMessage(errorMessage);
        if (processing.getStartedAt() == null) {
            processing.setStartedAt(now);
        }
        processing.setFinishedAt(now);
        processing.setUpdatedAt(now);
        chatPersistencePort.saveMessageProcessing(processing);
    }

    private void persistCitations(ChatMessage assistantMessage, List<ChatCitationResponse> citations) {
        for (ChatCitationResponse citation : citations) {
            ChatCitation entity = new ChatCitation();
            entity.setChatMessageId(assistantMessage.getId());
            entity.setSourceTitle(defaultString(citation.sourceTitle()));
            entity.setSourceSnippet(defaultString(citation.sourceSnippet()));
            entity.setSourceUrl(citation.sourceUrl());
            chatPersistencePort.saveCitation(entity);
        }
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private void applyMetadata(ChatMessage assistantMessage, ChatAssistantMetadata metadata) {
        ChatAssistantMetadata safeMetadata = metadata == null ? ChatAssistantMetadata.empty() : metadata;
        assistantMessage.setConfidenceStatus(blankToNull(safeMetadata.confidenceStatus()));
        assistantMessage.setConfidenceReason(blankToNull(safeMetadata.confidenceReason()));
        assistantMessage.setClarifyingQuestions(safeMetadata.clarifyingQuestions());
        assistantMessage.setPreliminaryActions(safeMetadata.preliminaryActions());
        assistantMessage.setSpecialistSupportRecommended(safeMetadata.specialistSupportRecommended());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void markUserMessageCompleted(UUID userMessageId, Instant now) {
        ChatMessage userMessage = chatPersistencePort.findMessageById(userMessageId).orElse(null);
        if (userMessage == null || userMessage.getRole() != ChatMessageRole.USER) {
            return;
        }
        ChatMessageProcessing processing = chatPersistencePort.findMessageProcessingByUserMessageIdForUpdate(userMessageId)
                .orElseGet(() -> initializeProcessingRecord(userMessageId, now));
        processing.setStatus(ChatMessageProcessingStatus.COMPLETED);
        processing.setFinishedAt(now);
        processing.setErrorCode(null);
        processing.setErrorMessage(null);
        if (processing.getStartedAt() == null) {
            processing.setStartedAt(now);
        }
        processing.setUpdatedAt(now);
        chatPersistencePort.saveMessageProcessing(processing);
    }

    private void markUserMessageFailed(UUID userMessageId, String errorCode, String errorMessage, Instant now) {
        ChatMessage userMessage = chatPersistencePort.findMessageById(userMessageId).orElse(null);
        if (userMessage == null || userMessage.getRole() != ChatMessageRole.USER) {
            return;
        }
        ChatMessageProcessing processing = chatPersistencePort.findMessageProcessingByUserMessageIdForUpdate(userMessageId)
                .orElseGet(() -> initializeProcessingRecord(userMessageId, now));
        processing.setStatus(ChatMessageProcessingStatus.FAILED);
        processing.setErrorCode(errorCode);
        processing.setErrorMessage(errorMessage);
        if (processing.getStartedAt() == null) {
            processing.setStartedAt(now);
        }
        processing.setFinishedAt(now);
        processing.setUpdatedAt(now);
        chatPersistencePort.saveMessageProcessing(processing);
    }

    private ChatMessageProcessing initializeProcessingRecord(UUID userMessageId, Instant now) {
        ChatMessageProcessing processing = new ChatMessageProcessing();
        processing.setUserMessageId(userMessageId);
        processing.setStatus(ChatMessageProcessingStatus.QUEUED);
        processing.setCreatedAt(now);
        processing.setUpdatedAt(now);
        return processing;
    }

}
