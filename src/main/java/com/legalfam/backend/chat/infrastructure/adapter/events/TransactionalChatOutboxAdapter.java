package com.legalfam.backend.chat.infrastructure.adapter.events;

import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import com.legalfam.backend.chat.application.port.out.ChatOutboxPort;
import com.legalfam.backend.chat.application.port.out.ChatPersistencePort;
import com.legalfam.backend.chat.domain.model.ChatOutboxEvent;
import com.legalfam.backend.chat.domain.model.ChatOutboxEventStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class TransactionalChatOutboxAdapter implements ChatOutboxPort {

    private static final Duration DEFAULT_EXPIRATION = Duration.ofHours(3);

    private final ChatPersistencePort chatPersistencePort;
    private final ObjectMapper objectMapper;

    public TransactionalChatOutboxAdapter(
            ChatPersistencePort chatPersistencePort,
            ObjectMapper objectMapper
    ) {
        this.chatPersistencePort = chatPersistencePort;
        this.objectMapper = objectMapper;
    }

    @Override
    public void enqueueMessageQueued(UUID chatSessionId, UUID userMessageId, String userMessageInput) {
        try {
            Instant now = Instant.now();
            ChatMessageQueuedEvent payload = new ChatMessageQueuedEvent(chatSessionId, userMessageId, userMessageInput);

            ChatOutboxEvent event = new ChatOutboxEvent();
            event.setEventType(ChatOutboxEvent.MESSAGE_QUEUED_EVENT_TYPE);
            event.setAggregateId(userMessageId);
            event.setChatSessionId(chatSessionId);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setStatus(ChatOutboxEventStatus.PENDING);
            event.setAttemptCount(0);
            event.setAvailableAt(now);
            event.setExpiresAt(now.plus(DEFAULT_EXPIRATION));
            event.setPublishedAt(null);
            event.setLastError(null);
            event.setCreatedAt(now);
            event.setUpdatedAt(now);

            chatPersistencePort.saveOutboxEvent(event);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to register chat message in transactional outbox", ex);
        }
    }
}
