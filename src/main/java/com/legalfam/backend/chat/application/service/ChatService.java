package com.legalfam.backend.chat.application.service;

import com.legalfam.backend.chat.application.port.in.ChatUseCase;
import com.legalfam.backend.chat.application.port.out.ChatOutboxPort;
import com.legalfam.backend.chat.application.port.out.ChatPersistencePort;
import com.legalfam.backend.chat.application.port.out.ChatUserLookupPort;
import com.legalfam.backend.chat.application.dto.ChatCitationResponse;
import com.legalfam.backend.chat.application.dto.ChatMessageResponse;
import com.legalfam.backend.chat.application.dto.ChatRateMessageRequest;
import com.legalfam.backend.chat.application.dto.ChatSendAcceptedResponse;
import com.legalfam.backend.chat.application.dto.ChatSessionResponse;
import com.legalfam.backend.chat.domain.exception.ChatAccessDeniedException;
import com.legalfam.backend.chat.domain.exception.ChatNotFoundException;
import com.legalfam.backend.chat.domain.exception.InvalidChatRequestException;
import com.legalfam.backend.chat.domain.model.ChatCitation;
import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessing;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessingStatus;
import com.legalfam.backend.chat.domain.model.ChatMessageRole;
import com.legalfam.backend.chat.domain.model.ChatSession;
import com.legalfam.backend.payment.application.port.in.PaymentTokenUseCase;
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
    private final ChatOutboxPort chatOutboxPort;
    private final ChatUserLookupPort chatUserLookupPort;
    private final PaymentTokenUseCase paymentTokenUseCase;

    public ChatService(
            ChatPersistencePort chatPersistencePort,
            ChatOutboxPort chatOutboxPort,
            ChatUserLookupPort chatUserLookupPort,
            PaymentTokenUseCase paymentTokenUseCase
    ) {
        this.chatPersistencePort = chatPersistencePort;
        this.chatOutboxPort = chatOutboxPort;
        this.chatUserLookupPort = chatUserLookupPort;
        this.paymentTokenUseCase = paymentTokenUseCase;
    }

    @Override
    @Transactional
    public ChatSendAcceptedResponse send(UUID userId, String messageInput, UUID sessionId) {
        if (sessionId == null) {
            throw new InvalidChatRequestException("Session id is required");
        }
        assertUserExists(userId);
        ChatSession chatSession = assertSessionOwnership(userId, sessionId);
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
        chatOutboxPort.enqueueMessageQueued(chatSession.getId(), userMessage.getId(), messageInput);

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
                .collect(Collectors.groupingBy(ChatCitation::getChatMessageId));

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
