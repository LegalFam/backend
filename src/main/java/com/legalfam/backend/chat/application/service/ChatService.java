package com.legalfam.backend.chat.application.service;

import com.legalfam.backend.chat.application.port.in.ChatUseCase;
import com.legalfam.backend.chat.application.port.out.ChatEventPublisherPort;
import com.legalfam.backend.chat.application.port.out.ChatPersistencePort;
import com.legalfam.backend.chat.application.dto.ChatCitationResponse;
import com.legalfam.backend.chat.application.dto.ChatMessageResponse;
import com.legalfam.backend.chat.application.dto.ChatRateMessageRequest;
import com.legalfam.backend.chat.application.dto.ChatSendAcceptedResponse;
import com.legalfam.backend.chat.application.dto.ChatSessionResponse;
import com.legalfam.backend.chat.domain.exception.ChatAccessDeniedException;
import com.legalfam.backend.chat.domain.exception.ChatNotFoundException;
import com.legalfam.backend.chat.domain.model.ChatCitation;
import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatMessageRole;
import com.legalfam.backend.chat.domain.model.ChatSession;
import com.legalfam.backend.common.error.exception.InvalidRequestException;
import com.legalfam.backend.user.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChatService implements ChatUseCase {

    private final ChatPersistencePort chatPersistencePort;
    private final ChatEventPublisherPort chatEventPublisherPort;

    public ChatService(
            ChatPersistencePort chatPersistencePort,
            ChatEventPublisherPort chatEventPublisherPort
    ) {
        this.chatPersistencePort = chatPersistencePort;
        this.chatEventPublisherPort = chatEventPublisherPort;
    }

    @Override
    @Transactional
    public ChatSendAcceptedResponse send(UUID userId, String messageInput, UUID sessionId) {
        if (sessionId == null) {
            throw new InvalidRequestException("Session id is required");
        }
        User user = chatPersistencePort.findUserById(userId)
                .orElseThrow(() -> new ChatAccessDeniedException("Authenticated user not found"));
        ChatSession chatSession = assertSessionOwnership(user.getId(), sessionId);
        Instant now = Instant.now();

        ChatMessage userMessage = new ChatMessage();
        userMessage.setChatSession(chatSession);
        userMessage.setRole(ChatMessageRole.USER);
        userMessage.setContent(messageInput);
        userMessage.setCreatedAt(now);
        userMessage = chatPersistencePort.saveMessage(userMessage);

        chatSession.setUpdatedAt(now);
        chatPersistencePort.saveSession(chatSession);
        chatEventPublisherPort.publishMessageQueued(chatSession.getId(), messageInput);

        return new ChatSendAcceptedResponse(chatSession.getId(), userMessage.getId(), "PROCESSING");
    }

    @Override
    @Transactional
    public ChatSessionResponse createSession(UUID userId) {
        User user = chatPersistencePort.findUserById(userId)
                .orElseThrow(() -> new ChatAccessDeniedException("Authenticated user not found"));
        Instant now = Instant.now();
        ChatSession session = new ChatSession();
        session.setUser(user);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session = chatPersistencePort.saveSession(session);
        return new ChatSessionResponse(session.getId(), session.getCreatedAt(), session.getUpdatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatSessionResponse> listSessions(UUID userId) {
        return chatPersistencePort.findSessionsByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(session -> new ChatSessionResponse(
                        session.getId(),
                        session.getCreatedAt(),
                        session.getUpdatedAt()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> listMessages(UUID userId, UUID sessionId) {
        ChatSession session = assertSessionOwnership(userId, sessionId);
        List<ChatMessage> messages = chatPersistencePort.findMessagesBySessionIdOrderByCreatedAtAsc(session.getId());
        if (messages.isEmpty()) {
            return List.of();
        }

        List<UUID> messageIds = messages.stream().map(ChatMessage::getId).toList();
        Map<UUID, List<ChatCitation>> citationsByMessageId = chatPersistencePort
                .findCitationsByMessageIdsOrderByMessageIdAndId(messageIds)
                .stream()
                .collect(Collectors.groupingBy(citation -> citation.getChatMessage().getId()));

        return messages.stream()
                .map(message -> new ChatMessageResponse(
                        message.getId(),
                        message.getRole().name(),
                        message.getContent(),
                        message.getRating(),
                        message.getCreatedAt(),
                        mapCitations(citationsByMessageId.getOrDefault(message.getId(), Collections.emptyList()))
                ))
                .toList();
    }

    @Override
    @Transactional
    public void rateMessage(UUID userId, UUID messageId, ChatRateMessageRequest request) {
        if (request == null || request.rating() == null) {
            throw new InvalidRequestException("Rating is required");
        }
        if (request.rating() < 1 || request.rating() > 5) {
            throw new InvalidRequestException("Rating must be between 1 and 5");
        }

        ChatMessage message = chatPersistencePort.findMessageById(messageId)
                .orElseThrow(() -> new ChatNotFoundException("Chat message not found"));

        UUID ownerId = message.getChatSession().getUser().getId();
        if (!ownerId.equals(userId)) {
            throw new ChatAccessDeniedException("Access is forbidden");
        }

        message.setRating(request.rating());
        chatPersistencePort.saveMessage(message);
    }

    @Override
    @Transactional(readOnly = true)
    public void assertSessionOwnershipExists(UUID userId, UUID sessionId) {
        assertSessionOwnership(userId, sessionId);
    }

    private ChatSession assertSessionOwnership(UUID userId, UUID sessionId) {
        ChatSession session = chatPersistencePort.findSessionById(sessionId)
                .orElseThrow(() -> new ChatNotFoundException("Chat session not found"));

        if (!session.getUser().getId().equals(userId)) {
            throw new ChatAccessDeniedException("Access is forbidden");
        }
        return session;
    }

    private List<ChatCitationResponse> mapCitations(List<ChatCitation> citations) {
        return citations.stream()
                .map(citation -> new ChatCitationResponse(
                        citation.getSourceTitle(),
                        citation.getSourceSnippet(),
                        citation.getSourceUrl()
                ))
                .toList();
    }

}
