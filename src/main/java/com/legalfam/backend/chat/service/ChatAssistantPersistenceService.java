package com.legalfam.backend.chat.service;

import com.legalfam.backend.chat.event.ChatAssistantMessageEvent;
import com.legalfam.backend.chat.event.ChatAssistantErrorEvent;
import com.legalfam.backend.chat.dto.ChatCitationResponse;
import com.legalfam.backend.chat.model.ChatCitation;
import com.legalfam.backend.chat.model.ChatMessage;
import com.legalfam.backend.chat.model.ChatMessageRole;
import com.legalfam.backend.chat.model.ChatSession;
import com.legalfam.backend.chat.repository.ChatCitationRepository;
import com.legalfam.backend.chat.repository.ChatMessageRepository;
import com.legalfam.backend.chat.repository.ChatSessionRepository;
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

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatCitationRepository chatCitationRepository;

    public ChatAssistantPersistenceService(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            ChatCitationRepository chatCitationRepository
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatCitationRepository = chatCitationRepository;
    }

    @Transactional
    public ChatAssistantMessageDispatch persistAssistantMessage(
            UUID chatSessionId,
            String assistantMessageText,
            List<ChatCitationResponse> citations
    ) {
        ChatSession chatSession = chatSessionRepository.findById(chatSessionId).orElse(null);
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
        assistantMessage = chatMessageRepository.save(assistantMessage);

        persistCitations(assistantMessage, citations);

        chatSession.setUpdatedAt(now);
        chatSessionRepository.save(chatSession);

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
        ChatSession chatSession = chatSessionRepository.findById(chatSessionId).orElse(null);
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
        failureMessage = chatMessageRepository.save(failureMessage);

        chatSession.setUpdatedAt(now);
        chatSessionRepository.save(chatSession);

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
            chatCitationRepository.save(entity);
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
