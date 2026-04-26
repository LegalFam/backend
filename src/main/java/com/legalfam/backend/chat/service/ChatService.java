package com.legalfam.backend.chat.service;

import com.legalfam.backend.chat.dto.ChatCitationResponse;
import com.legalfam.backend.chat.dto.ChatMessageResponse;
import com.legalfam.backend.chat.dto.ChatRateMessageRequest;
import com.legalfam.backend.chat.dto.ChatSendAcceptedResponse;
import com.legalfam.backend.chat.dto.ChatSessionResponse;
import com.legalfam.backend.chat.event.ChatMessageQueuedEvent;
import com.legalfam.backend.chat.exception.ChatAccessDeniedException;
import com.legalfam.backend.chat.exception.ChatNotFoundException;
import com.legalfam.backend.chat.model.ChatCitation;
import com.legalfam.backend.chat.model.ChatMessage;
import com.legalfam.backend.chat.model.ChatMessageRole;
import com.legalfam.backend.chat.model.ChatSession;
import com.legalfam.backend.chat.repository.ChatCitationRepository;
import com.legalfam.backend.chat.repository.ChatMessageRepository;
import com.legalfam.backend.chat.repository.ChatSessionRepository;
import com.legalfam.backend.error.exception.InvalidRequestException;
import com.legalfam.backend.user.User;
import com.legalfam.backend.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private final UserRepository userRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatCitationRepository chatCitationRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ChatService(
            UserRepository userRepository,
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            ChatCitationRepository chatCitationRepository,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.userRepository = userRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatCitationRepository = chatCitationRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public ChatSendAcceptedResponse send(UUID userId, String messageInput, UUID sessionId) {
        if (sessionId == null) {
            throw new InvalidRequestException("Session id is required");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ChatAccessDeniedException("Authenticated user not found"));
        ChatSession chatSession = assertSessionOwnership(user.getId(), sessionId);
        Instant now = Instant.now();

        ChatMessage userMessage = new ChatMessage();
        userMessage.setChatSession(chatSession);
        userMessage.setRole(ChatMessageRole.USER);
        userMessage.setContent(messageInput);
        userMessage.setCreatedAt(now);
        userMessage = chatMessageRepository.save(userMessage);

        chatSession.setUpdatedAt(now);
        chatSessionRepository.save(chatSession);
        applicationEventPublisher.publishEvent(new ChatMessageQueuedEvent(chatSession.getId(), messageInput));

        return new ChatSendAcceptedResponse(chatSession.getId(), userMessage.getId(), "PROCESSING");
    }

    @Transactional
    public ChatSessionResponse createSession(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ChatAccessDeniedException("Authenticated user not found"));
        Instant now = Instant.now();
        ChatSession session = new ChatSession();
        session.setUser(user);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session = chatSessionRepository.save(session);
        return new ChatSessionResponse(session.getId(), session.getCreatedAt(), session.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> listSessions(UUID userId) {
        return chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(session -> new ChatSessionResponse(
                        session.getId(),
                        session.getCreatedAt(),
                        session.getUpdatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> listMessages(UUID userId, UUID sessionId) {
        ChatSession session = assertSessionOwnership(userId, sessionId);
        List<ChatMessage> messages = chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(session.getId());
        if (messages.isEmpty()) {
            return List.of();
        }

        List<UUID> messageIds = messages.stream().map(ChatMessage::getId).toList();
        Map<UUID, List<ChatCitation>> citationsByMessageId = chatCitationRepository
                .findByChatMessageIdInOrderByChatMessageIdAscIdAsc(messageIds)
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

    @Transactional
    public void rateMessage(UUID userId, UUID messageId, ChatRateMessageRequest request) {
        if (request == null || request.rating() == null) {
            throw new InvalidRequestException("Rating is required");
        }
        if (request.rating() < 1 || request.rating() > 5) {
            throw new InvalidRequestException("Rating must be between 1 and 5");
        }

        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ChatNotFoundException("Chat message not found"));

        UUID ownerId = message.getChatSession().getUser().getId();
        if (!ownerId.equals(userId)) {
            throw new ChatAccessDeniedException("Access is forbidden");
        }

        message.setRating(request.rating());
        chatMessageRepository.save(message);
    }

    @Transactional(readOnly = true)
    public void assertSessionOwnershipExists(UUID userId, UUID sessionId) {
        assertSessionOwnership(userId, sessionId);
    }

    private ChatSession assertSessionOwnership(UUID userId, UUID sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
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

