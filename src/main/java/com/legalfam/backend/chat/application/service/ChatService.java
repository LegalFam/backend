package com.legalfam.backend.chat.application.service;

import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import com.legalfam.backend.chat.application.mapper.ChatMessageResponseMapper;
import com.legalfam.backend.chat.application.policy.ChatAccessPolicy;
import com.legalfam.backend.chat.application.policy.ChatPrivacyPolicy;
import com.legalfam.backend.chat.application.port.in.IChatUseCase;
import com.legalfam.backend.chat.application.port.out.IChatEventPublisherPort;
import com.legalfam.backend.chat.application.port.out.IChatPersistencePort;
import com.legalfam.backend.chat.application.port.out.IChatTokenPort;
import com.legalfam.backend.chat.application.dto.ChatMessageResponse;
import com.legalfam.backend.chat.application.dto.ChatRateMessageRequest;
import com.legalfam.backend.chat.application.dto.ChatSendAcceptedResponse;
import com.legalfam.backend.chat.application.dto.ChatSessionResponse;
import com.legalfam.backend.chat.application.dto.ChatUpdateSessionRequest;
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
import com.legalfam.backend.common.cursor.CursorQuery;
import com.legalfam.backend.common.cursor.CursorResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService implements IChatUseCase {

    private final IChatPersistencePort IChatPersistencePort;
    private final IChatTokenPort IChatTokenPort;
    private final IChatEventPublisherPort IChatEventPublisherPort;
    private final ChatAccessPolicy chatAccessPolicy;
    private final ChatPrivacyPolicy chatPrivacyPolicy;
    private final ChatMessageResponseMapper chatMessageResponseMapper;

    public ChatService(
            IChatPersistencePort IChatPersistencePort,
            IChatTokenPort IChatTokenPort,
            IChatEventPublisherPort IChatEventPublisherPort,
            ChatAccessPolicy chatAccessPolicy,
            ChatPrivacyPolicy chatPrivacyPolicy,
            ChatMessageResponseMapper chatMessageResponseMapper
    ) {
        this.IChatPersistencePort = IChatPersistencePort;
        this.IChatTokenPort = IChatTokenPort;
        this.IChatEventPublisherPort = IChatEventPublisherPort;
        this.chatAccessPolicy = chatAccessPolicy;
        this.chatPrivacyPolicy = chatPrivacyPolicy;
        this.chatMessageResponseMapper = chatMessageResponseMapper;
    }

    @Override
    @Transactional
    public ChatSendAcceptedResponse send(UUID userId, String messageInput, UUID sessionId) {
        if (sessionId == null) {
            throw new InvalidChatRequestException("Session id is required");
        }
        chatPrivacyPolicy.assertAllowed(messageInput);
        chatAccessPolicy.assertUserExists(userId);
        ChatSession chatSession = chatAccessPolicy.requireSessionOwner(userId, sessionId);
        if (IChatPersistencePort.existsUnreadAssistantMessageBySessionId(chatSession.getId())) {
            throw new PendingAssistantMessageException("Assistant receipt confirmation is still pending for this session");
        }
        Instant now = Instant.now();

        ChatMessage userMessage = ChatMessage.userMessage(chatSession.getId(), messageInput, now);

        IChatTokenPort.consumeChatToken(userId, userMessage.getId());
        userMessage = IChatPersistencePort.saveMessage(userMessage);

        ChatMessageProcessing messageProcessing = new ChatMessageProcessing();
        messageProcessing.setUserMessageId(userMessage.getId());
        messageProcessing.setStatus(ChatMessageProcessingStatus.QUEUED);
        messageProcessing.setCreatedAt(now);
        messageProcessing.setUpdatedAt(now);
        IChatPersistencePort.saveMessageProcessing(messageProcessing);

        chatSession.touch(now);
        IChatPersistencePort.saveSession(chatSession);
        IChatEventPublisherPort.publishMessageQueued(new ChatMessageQueuedEvent(chatSession.getId(), userMessage.getId(), messageInput));

        return new ChatSendAcceptedResponse(chatSession.getId(), userMessage.getId(), "PROCESSING");
    }

    @Override
    @Transactional
    public ChatSessionResponse createSession(UUID userId) {
        chatAccessPolicy.assertUserExists(userId);
        Instant now = Instant.now();
        ChatSession session = ChatSession.create(userId, now);
        session = IChatPersistencePort.saveSession(session);
        return toSessionResponse(session);
    }

    @Override
    @Transactional
    public ChatSessionResponse updateSession(UUID userId, UUID sessionId, ChatUpdateSessionRequest request) {
        if (request == null) {
            throw new InvalidChatRequestException("Session title is required");
        }

        ChatSession session = chatAccessPolicy.requireSessionOwner(userId, sessionId);
        session.rename(request.title(), Instant.now());
        return toSessionResponse(IChatPersistencePort.saveSession(session));
    }

    @Override
    @Transactional
    public void deleteSession(UUID userId, UUID sessionId) {
        ChatSession session = chatAccessPolicy.requireSessionOwner(userId, sessionId);
        IChatPersistencePort.deleteSessionById(session.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public CursorResult<ChatSessionResponse> listSessions(UUID userId, CursorQuery cursorQuery) {
        return IChatPersistencePort.findSessionsByUserIdOrderByUpdatedAtDesc(userId, cursorQuery)
                .map(this::toSessionResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorResult<ChatMessageResponse> listMessages(UUID userId, UUID sessionId, CursorQuery cursorQuery) {
        ChatSession session = chatAccessPolicy.requireSessionOwner(userId, sessionId);
        CursorResult<ChatMessage> messageBatch = IChatPersistencePort.findMessagesBySessionIdOrderByCreatedAtDesc(
                session.getId(),
                cursorQuery
        );
        List<ChatMessage> messages = new ArrayList<>(messageBatch.content());
        Collections.reverse(messages);
        messageBatch = new CursorResult<>(messages, messageBatch.nextCursor());
        if (messages.isEmpty()) {
            return messageBatch.map(message -> chatMessageResponseMapper.toResponse(message, Map.of(), Map.of()));
        }

        List<UUID> messageIds = messages.stream().map(ChatMessage::getId).toList();
        Map<UUID, List<ChatCitation>> citationsByMessageId = IChatPersistencePort
                .findCitationsByMessageIdsOrderByMessageIdAndId(messageIds)
                .stream()
                .collect(Collectors.groupingBy(ChatCitation::getChatMessageId));
        Map<UUID, ChatOutboxEvent> outboxByMessageId = IChatPersistencePort.findOutboxEventsByAggregateIds(messageIds)
                .stream()
                .collect(Collectors.toMap(ChatOutboxEvent::getAggregateId, event -> event));

        return messageBatch.map(message -> chatMessageResponseMapper.toResponse(message, citationsByMessageId, outboxByMessageId));
    }

    @Override
    @Transactional
    public void rateMessage(UUID userId, UUID messageId, ChatRateMessageRequest request) {
        if (request == null || request.rating() == null) {
            throw new InvalidChatRequestException("Rating is required");
        }

        ChatMessage message = IChatPersistencePort.findMessageById(messageId)
                .orElseThrow(() -> new ChatNotFoundException("Chat message not found"));
        chatAccessPolicy.requireMessageSessionOwner(userId, message);
        message.submitFeedback(request.rating(), request.comment(), Instant.now());
        IChatPersistencePort.saveMessage(message);
    }

    @Override
    @Transactional
    public void confirmAssistantReceipt(UUID userId, UUID messageId) {
        ChatMessage message = IChatPersistencePort.findMessageById(messageId)
                .orElseThrow(() -> new ChatNotFoundException("Chat message not found"));
        if (message.getRole() != ChatMessageRole.ASSISTANT) {
            throw new InvalidChatRequestException("Receipt can only be confirmed for assistant messages");
        }

        ChatSession messageSession = chatAccessPolicy.requireSessionOwner(userId, message.getChatSessionId());
        ChatOutboxEvent outboxEvent = IChatPersistencePort.findOutboxEventByAggregateIdForUpdate(messageId)
                .orElseThrow(() -> new ChatNotFoundException("Assistant delivery event not found"));

        Instant now = Instant.now();
        outboxEvent.setStatus(ChatOutboxEventStatus.READ);
        outboxEvent.setReadAt(now);
        outboxEvent.setUpdatedAt(now);
        IChatPersistencePort.saveOutboxEvent(outboxEvent);

        messageSession.touch(now);
        IChatPersistencePort.saveSession(messageSession);
    }

    @Override
    @Transactional(readOnly = true)
    public void assertSessionOwnershipExists(UUID userId, UUID sessionId) {
        chatAccessPolicy.requireSessionOwner(userId, sessionId);
    }

    private ChatSessionResponse toSessionResponse(ChatSession session) {
        return new ChatSessionResponse(
                session.getId(),
                session.getTitle(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

}
