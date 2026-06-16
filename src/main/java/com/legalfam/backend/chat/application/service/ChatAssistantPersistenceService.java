package com.legalfam.backend.chat.application.service;

import com.legalfam.backend.chat.application.dto.ChatAssistantErrorDispatch;
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
import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessing;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessingStatus;
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
        IChatPersistencePort.saveMessageProcessing(processing);
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
        ChatSession chatSession = IChatPersistencePort.findSessionById(chatSessionId).orElse(null);
        if (chatSession == null) {
            log.warn("Skipping assistant response persistence: chat session not found chatSessionId={}", chatSessionId);
            return null;
        }

        Instant now = Instant.now();
        ChatMessage assistantMessage = ChatMessage.assistantMessage(chatSession.getId(), assistantMessageText, now);
        applyMetadata(assistantMessage, metadata);
        assistantMessage = IChatPersistencePort.saveMessage(assistantMessage);

        persistCitations(assistantMessage, citations);
        consumeTokensForAssistantResult(chatSession.getUserId(), userMessageId, metadata);
        markUserMessageCompleted(userMessageId, now);

        chatSession.touch(now);
        IChatPersistencePort.saveSession(chatSession);

        ChatAssistantMessageEvent assistantMessageEvent = new ChatAssistantMessageEvent(
                chatSession.getId(),
                assistantMessage.getId(),
                assistantMessageText,
                assistantMessage.getCreatedAt(),
                citations,
                assistantMessage.getConfidenceStatus(),
                assistantMessage.getConfidenceReason(),
                assistantMessage.getNextSteps(),
                assistantMessage.getSpecialistSupportRecommended(),
                assistantMessage.getCitationSupportStatus(),
                "PENDING",
                true
        );
        IChatOutboxPort.enqueueAssistantDelivery(new ChatAssistantDeliveryQueuedEvent(
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
        ChatSession chatSession = IChatPersistencePort.findSessionById(chatSessionId).orElse(null);
        if (chatSession == null) {
            log.warn("Skipping assistant failure persistence: chat session not found chatSessionId={}", chatSessionId);
            return null;
        }

        Instant now = Instant.now();
        ChatMessage failureMessage = ChatMessage.systemMessage(chatSession.getId(), errorMessage, now);
        failureMessage = IChatPersistencePort.saveMessage(failureMessage);
        markUserMessageFailed(userMessageId, errorCode, errorMessage, now);

        chatSession.touch(now);
        IChatPersistencePort.saveSession(chatSession);
        IChatTokenPort.refundChatToken(userMessageId);

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
        ChatMessage userMessage = IChatPersistencePort.findMessageById(userMessageId).orElse(null);
        if (userMessage == null || userMessage.getRole() != ChatMessageRole.USER) {
            return;
        }
        ChatMessageProcessing processing = IChatPersistencePort.findMessageProcessingByUserMessageIdForUpdate(userMessageId)
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
        IChatPersistencePort.saveMessageProcessing(processing);
    }

    private void persistCitations(ChatMessage assistantMessage, List<ChatCitationResponse> citations) {
        for (ChatCitationResponse citation : citations) {
            ChatCitation entity = new ChatCitation();
            entity.setChatMessageId(assistantMessage.getId());
            entity.setSourceTitle(defaultString(citation.sourceTitle()));
            entity.setSourceSnippet(defaultString(citation.sourceSnippet()));
            entity.setSourceUrl(citation.sourceUrl());
            IChatPersistencePort.saveCitation(entity);
        }
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
                safeMetadata.specialistSupportRecommended(),
                safeMetadata.citationSupportStatus()
        );
    }

    private void consumeTokensForAssistantResult(UUID userId, UUID userMessageId, ChatAssistantMetadata metadata) {
        ChatAssistantMetadata safeMetadata = metadata == null ? ChatAssistantMetadata.empty() : metadata;
        int agentTokenCost = safeMetadata.agentTokenCost() == null ? 1 : safeMetadata.agentTokenCost();
        IChatTokenPort.consumeChatTokensForAssistantResult(userId, userMessageId, agentTokenCost);
    }

    private void markUserMessageCompleted(UUID userMessageId, Instant now) {
        ChatMessage userMessage = IChatPersistencePort.findMessageById(userMessageId).orElse(null);
        if (userMessage == null || userMessage.getRole() != ChatMessageRole.USER) {
            return;
        }
        ChatMessageProcessing processing = IChatPersistencePort.findMessageProcessingByUserMessageIdForUpdate(userMessageId)
                .orElseGet(() -> initializeProcessingRecord(userMessageId, now));
        processing.setStatus(ChatMessageProcessingStatus.COMPLETED);
        processing.setFinishedAt(now);
        processing.setErrorCode(null);
        processing.setErrorMessage(null);
        if (processing.getStartedAt() == null) {
            processing.setStartedAt(now);
        }
        processing.setUpdatedAt(now);
        IChatPersistencePort.saveMessageProcessing(processing);
    }

    private void markUserMessageFailed(UUID userMessageId, String errorCode, String errorMessage, Instant now) {
        ChatMessage userMessage = IChatPersistencePort.findMessageById(userMessageId).orElse(null);
        if (userMessage == null || userMessage.getRole() != ChatMessageRole.USER) {
            return;
        }
        ChatMessageProcessing processing = IChatPersistencePort.findMessageProcessingByUserMessageIdForUpdate(userMessageId)
                .orElseGet(() -> initializeProcessingRecord(userMessageId, now));
        processing.setStatus(ChatMessageProcessingStatus.FAILED);
        processing.setErrorCode(errorCode);
        processing.setErrorMessage(errorMessage);
        if (processing.getStartedAt() == null) {
            processing.setStartedAt(now);
        }
        processing.setFinishedAt(now);
        processing.setUpdatedAt(now);
        IChatPersistencePort.saveMessageProcessing(processing);
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
