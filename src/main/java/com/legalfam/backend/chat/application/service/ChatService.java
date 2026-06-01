package com.legalfam.backend.chat.application.service;

import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import com.legalfam.backend.chat.application.port.in.ChatUseCase;
import com.legalfam.backend.chat.application.port.out.ChatPersistencePort;
import com.legalfam.backend.chat.application.port.out.ChatUserLookupPort;
import com.legalfam.backend.chat.application.dto.ChatCitationResponse;
import com.legalfam.backend.chat.application.dto.ChatMessageResponse;
import com.legalfam.backend.chat.application.dto.ChatRateMessageRequest;
import com.legalfam.backend.chat.application.dto.ChatSendAcceptedResponse;
import com.legalfam.backend.chat.application.dto.ChatSessionResponse;
import com.legalfam.backend.chat.application.dto.ChatUpdateSessionRequest;
import com.legalfam.backend.chat.domain.exception.ChatAccessDeniedException;
import com.legalfam.backend.chat.domain.exception.ChatNotFoundException;
import com.legalfam.backend.chat.domain.exception.InvalidChatRequestException;
import com.legalfam.backend.chat.domain.exception.PendingAssistantMessageException;
import com.legalfam.backend.chat.domain.model.ChatCitation;
import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessing;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessingStatus;
import com.legalfam.backend.chat.domain.model.ChatMessageRole;
import com.legalfam.backend.chat.domain.model.ChatOutboxEvent;
import com.legalfam.backend.chat.domain.model.ChatOutboxEventStatus;
import com.legalfam.backend.chat.domain.model.ChatSession;
import com.legalfam.backend.payment.application.port.in.PaymentTokenUseCase;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService implements ChatUseCase {

    private final ChatPersistencePort chatPersistencePort;
    private final ChatUserLookupPort chatUserLookupPort;
    private final PaymentTokenUseCase paymentTokenUseCase;
    private final ApplicationEventPublisher applicationEventPublisher;
    private static final int MAX_SESSION_TITLE_LENGTH = 80;

    public ChatService(
            ChatPersistencePort chatPersistencePort,
            ChatUserLookupPort chatUserLookupPort,
            PaymentTokenUseCase paymentTokenUseCase,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.chatPersistencePort = chatPersistencePort;
        this.chatUserLookupPort = chatUserLookupPort;
        this.paymentTokenUseCase = paymentTokenUseCase;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    @Transactional
    public ChatSendAcceptedResponse send(UUID userId, String messageInput, UUID sessionId) {
        if (sessionId == null) {
            throw new InvalidChatRequestException("Session id is required");
        }
        assertUserExists(userId);
        ChatSession chatSession = assertSessionOwnership(userId, sessionId);
        if (chatPersistencePort.existsUnreadAssistantMessageBySessionId(chatSession.getId())) {
            throw new PendingAssistantMessageException("Assistant receipt confirmation is still pending for this session");
        }
        Instant now = Instant.now();

        ChatMessage userMessage = new ChatMessage();
        userMessage.setChatSessionId(chatSession.getId());
        userMessage.setRole(ChatMessageRole.USER);
        userMessage.setContent(messageInput);
        userMessage.setCreatedAt(now);
        userMessage = chatPersistencePort.saveMessage(userMessage);

        ChatMessageProcessing messageProcessing = new ChatMessageProcessing();
        messageProcessing.setUserMessageId(userMessage.getId());
        messageProcessing.setStatus(ChatMessageProcessingStatus.QUEUED);
        messageProcessing.setCreatedAt(now);
        messageProcessing.setUpdatedAt(now);
        chatPersistencePort.saveMessageProcessing(messageProcessing);

        paymentTokenUseCase.consumeChatToken(userId, userMessage.getId());

        chatSession.setUpdatedAt(now);
        chatPersistencePort.saveSession(chatSession);
        applicationEventPublisher.publishEvent(new ChatMessageQueuedEvent(chatSession.getId(), userMessage.getId(), messageInput));

        return new ChatSendAcceptedResponse(chatSession.getId(), userMessage.getId(), "PROCESSING");
    }

    @Override
    @Transactional
    public ChatSessionResponse createSession(UUID userId) {
        assertUserExists(userId);
        Instant now = Instant.now();
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session = chatPersistencePort.saveSession(session);
        return toSessionResponse(session);
    }

    @Override
    @Transactional
    public ChatSessionResponse updateSession(UUID userId, UUID sessionId, ChatUpdateSessionRequest request) {
        if (request == null || request.title() == null || request.title().isBlank()) {
            throw new InvalidChatRequestException("Session title is required");
        }

        ChatSession session = assertSessionOwnership(userId, sessionId);
        session.setTitle(normalizeSessionTitle(request.title()));
        session.setUpdatedAt(Instant.now());
        return toSessionResponse(chatPersistencePort.saveSession(session));
    }

    @Override
    @Transactional
    public void deleteSession(UUID userId, UUID sessionId) {
        ChatSession session = assertSessionOwnership(userId, sessionId);
        chatPersistencePort.deleteSessionById(session.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatSessionResponse> listSessions(UUID userId) {
        return chatPersistencePort.findSessionsByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toSessionResponse)
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
                .collect(Collectors.groupingBy(ChatCitation::getChatMessageId));
        Map<UUID, ChatOutboxEvent> outboxByMessageId = chatPersistencePort.findOutboxEventsByAggregateIds(messageIds)
                .stream()
                .collect(Collectors.toMap(ChatOutboxEvent::getAggregateId, event -> event));

        return messages.stream()
                .map(message -> new ChatMessageResponse(
                        message.getId(),
                        message.getRole().name(),
                        message.getContent(),
                        message.getRating(),
                        message.getCreatedAt(),
                        mapCitations(citationsByMessageId.getOrDefault(message.getId(), Collections.emptyList())),
                        resolveReceiptStatus(message, outboxByMessageId.get(message.getId())),
                        resolveReadAt(message, outboxByMessageId.get(message.getId()))
                ))
                .toList();
    }

    @Override
    @Transactional
    public void rateMessage(UUID userId, UUID messageId, ChatRateMessageRequest request) {
        if (request == null || request.rating() == null) {
            throw new InvalidChatRequestException("Rating is required");
        }
        if (request.rating() < 1 || request.rating() > 5) {
            throw new InvalidChatRequestException("Rating must be between 1 and 5");
        }

        ChatMessage message = chatPersistencePort.findMessageById(messageId)
                .orElseThrow(() -> new ChatNotFoundException("Chat message not found"));

        ChatSession messageSession = chatPersistencePort.findSessionById(message.getChatSessionId())
                .orElseThrow(() -> new ChatNotFoundException("Chat session not found"));
        UUID ownerId = messageSession.getUserId();
        if (!ownerId.equals(userId)) {
            throw new ChatAccessDeniedException("Access is forbidden");
        }

        message.setRating(request.rating());
        chatPersistencePort.saveMessage(message);
    }

    @Override
    @Transactional
    public void confirmAssistantReceipt(UUID userId, UUID messageId) {
        ChatMessage message = chatPersistencePort.findMessageById(messageId)
                .orElseThrow(() -> new ChatNotFoundException("Chat message not found"));
        if (message.getRole() != ChatMessageRole.ASSISTANT) {
            throw new InvalidChatRequestException("Receipt can only be confirmed for assistant messages");
        }

        ChatSession messageSession = assertSessionOwnership(userId, message.getChatSessionId());
        ChatOutboxEvent outboxEvent = chatPersistencePort.findOutboxEventByAggregateIdForUpdate(messageId)
                .orElseThrow(() -> new ChatNotFoundException("Assistant delivery event not found"));

        Instant now = Instant.now();
        outboxEvent.setStatus(ChatOutboxEventStatus.READ);
        outboxEvent.setReadAt(now);
        outboxEvent.setUpdatedAt(now);
        chatPersistencePort.saveOutboxEvent(outboxEvent);

        messageSession.setUpdatedAt(now);
        chatPersistencePort.saveSession(messageSession);
    }

    @Override
    @Transactional(readOnly = true)
    public void assertSessionOwnershipExists(UUID userId, UUID sessionId) {
        assertSessionOwnership(userId, sessionId);
    }

    private ChatSession assertSessionOwnership(UUID userId, UUID sessionId) {
        ChatSession session = chatPersistencePort.findSessionById(sessionId)
                .orElseThrow(() -> new ChatNotFoundException("Chat session not found"));

        if (!session.getUserId().equals(userId)) {
            throw new ChatAccessDeniedException("Access is forbidden");
        }
        return session;
    }

    private void assertUserExists(UUID userId) {
        if (!chatUserLookupPort.existsById(userId)) {
            throw new ChatAccessDeniedException("Authenticated user not found");
        }
    }

    private ChatSessionResponse toSessionResponse(ChatSession session) {
        return new ChatSessionResponse(
                session.getId(),
                session.getTitle(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

    private String normalizeSessionTitle(String title) {
        String normalized = title.trim();
        if (normalized.length() > MAX_SESSION_TITLE_LENGTH) {
            normalized = normalized.substring(0, MAX_SESSION_TITLE_LENGTH);
        }
        return normalized;
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

    private String resolveReceiptStatus(ChatMessage message, ChatOutboxEvent event) {
        if (message.getRole() != ChatMessageRole.ASSISTANT || event == null) {
            return null;
        }
        return event.getStatus().name();
    }

    private Instant resolveReadAt(ChatMessage message, ChatOutboxEvent event) {
        if (message.getRole() != ChatMessageRole.ASSISTANT || event == null) {
            return null;
        }
        return event.getReadAt();
    }

}
