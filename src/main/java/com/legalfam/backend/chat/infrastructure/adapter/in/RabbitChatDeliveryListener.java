package com.legalfam.backend.chat.infrastructure.adapter.in;

import com.legalfam.backend.chat.application.event.ChatAssistantDeliveryQueuedEvent;
import com.legalfam.backend.chat.application.port.out.IChatPersistencePort;
import com.legalfam.backend.chat.domain.model.ChatOutboxEvent;
import com.legalfam.backend.chat.domain.model.ChatOutboxEventStatus;
import com.legalfam.backend.chat.infrastructure.delivery.ChatSseEmitterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(name = "app.chat.messaging.rabbit.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitChatDeliveryListener {

    private static final Duration RETRY_DELAY = Duration.ofMinutes(10);

    private final ObjectMapper objectMapper;
    private final IChatPersistencePort IChatPersistencePort;
    private final ChatSseEmitterRegistry chatSseEmitterRegistry;

    public RabbitChatDeliveryListener(
            ObjectMapper objectMapper,
            IChatPersistencePort IChatPersistencePort,
            ChatSseEmitterRegistry chatSseEmitterRegistry
    ) {
        this.objectMapper = objectMapper;
        this.IChatPersistencePort = IChatPersistencePort;
        this.chatSseEmitterRegistry = chatSseEmitterRegistry;
    }

    @RabbitListener(
            queues = "${app.chat.messaging.rabbit.queue.assistant-delivery}",
            concurrency = "${app.chat.messaging.rabbit.listener.concurrency:1}"
    )
    @Transactional
    public void process(String payload) {
        ChatAssistantDeliveryQueuedEvent event = parseEvent(payload);
        ChatOutboxEvent outboxEvent = IChatPersistencePort.findOutboxEventByAggregateIdForUpdate(event.assistantMessageId())
                .orElse(null);
        if (outboxEvent == null || outboxEvent.getStatus() == ChatOutboxEventStatus.READ) {
            return;
        }

        Instant now = Instant.now();
        boolean delivered = chatSseEmitterRegistry.dispatchAssistantMessage(event.userId(), event.chatSessionId(), event.event());
        outboxEvent.setAttemptCount(outboxEvent.getAttemptCount() + 1);
        outboxEvent.setAvailableAt(now.plus(RETRY_DELAY));
        outboxEvent.setStatus(delivered ? ChatOutboxEventStatus.PUBLISHED : ChatOutboxEventStatus.PENDING);
        outboxEvent.setPublishedAt(delivered ? now : outboxEvent.getPublishedAt());
        outboxEvent.setLastError(delivered ? null : "No active SSE subscriber available");
        outboxEvent.setUpdatedAt(now);
        IChatPersistencePort.saveOutboxEvent(outboxEvent);
    }

    private ChatAssistantDeliveryQueuedEvent parseEvent(String payload) {
        try {
            return objectMapper.readValue(payload, ChatAssistantDeliveryQueuedEvent.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid assistant delivery event payload", ex);
        }
    }
}
