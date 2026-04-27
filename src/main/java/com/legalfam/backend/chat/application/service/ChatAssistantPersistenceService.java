package com.legalfam.backend.chat.application.service;

import com.legalfam.backend.chat.application.port.out.ChatPersistencePort;
import com.legalfam.backend.chat.application.event.ChatAssistantMessageEvent;
import com.legalfam.backend.chat.application.event.ChatAssistantErrorEvent;
import com.legalfam.backend.chat.application.dto.ChatCitationResponse;
import com.legalfam.backend.chat.domain.model.ChatCitation;
import com.legalfam.backend.chat.domain.model.ChatMessage;
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
public class ChatAssistantPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ChatAssistantPersistenceService.class);

    private final ChatPersistencePort chatPersistencePort;

    public ChatAssistantPersistenceService(ChatPersistencePort chatPersistencePort) {
        this.chatPersistencePort = chatPersistencePort;
    }

    @Transactional
    public ChatAssistantMessageDispatch persistAssistantMessage(
            UUID chatSessionId,
            String assistantMessageText,
            List<ChatCitationResponse> citations
    ) {
        ChatSession chatSession = chatPersistencePort.findSessionById(chatSessionId).orElse(null);
        if (chatSession == null) {
            log.warn("Skipping assistant response persistence: chat session not found chatSessionId={}", chatSessionId);
            return null;
        }

        Instant now = Instant.now();
        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setChatSession(chatSession);
        assistantMessage.setRole(ChatMessageRole.ASSISTANT);
        assistantMessage.setContent(assistantMessageText);
        assistantMessage.setCreatedAt(now);
        assistantMessage = chatPersistencePort.saveMessage(assistantMessage);

        persistCitations(assistantMessage, citations);

        chatSession.setUpdatedAt(now);
        chatPersistencePort.saveSession(chatSession);

        return new ChatAssistantMessageDispatch(
                chatSession.getUser().getId(),
                chatSession.getId(),
                new ChatAssistantMessageEvent(
                        chatSession.getId(),
                        assistantMessage.getId(),
                        assistantMessageText,
                        assistantMessage.getCreatedAt(),
                        citations
                )
        );
    }

    @Transactional
    public ChatAssistantErrorDispatch persistAssistantFailure(
            UUID chatSessionId,
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
        failureMessage.setChatSession(chatSession);
        failureMessage.setRole(ChatMessageRole.SYSTEM);
        failureMessage.setContent(errorMessage);
        failureMessage.setCreatedAt(now);
        failureMessage = chatPersistencePort.saveMessage(failureMessage);

        chatSession.setUpdatedAt(now);
        chatPersistencePort.saveSession(chatSession);

        return new ChatAssistantErrorDispatch(
                chatSession.getUser().getId(),
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

    private void persistCitations(ChatMessage assistantMessage, List<ChatCitationResponse> citations) {
        for (ChatCitationResponse citation : citations) {
            ChatCitation entity = new ChatCitation();
            entity.setChatMessage(assistantMessage);
            entity.setSourceTitle(defaultString(citation.sourceTitle()));
            entity.setSourceSnippet(defaultString(citation.sourceSnippet()));
            entity.setSourceUrl(citation.sourceUrl());
            chatPersistencePort.saveCitation(entity);
        }
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    public record ChatAssistantMessageDispatch(
            UUID userId,
            UUID chatSessionId,
            ChatAssistantMessageEvent event
    ) {
    }

    public record ChatAssistantErrorDispatch(
            UUID userId,
            UUID chatSessionId,
            ChatAssistantErrorEvent event
    ) {
    }
}
