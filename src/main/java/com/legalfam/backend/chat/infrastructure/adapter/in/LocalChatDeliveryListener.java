package com.legalfam.backend.chat.infrastructure.adapter.in;

import com.legalfam.backend.chat.application.event.ChatAssistantDeliveryQueuedEvent;
import com.legalfam.backend.chat.application.port.out.IChatPersistencePort;
import com.legalfam.backend.chat.domain.model.ChatOutboxEvent;
import com.legalfam.backend.chat.domain.model.ChatOutboxEventStatus;
import com.legalfam.backend.chat.infrastructure.delivery.ChatSseEmitterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@ConditionalOnProperty(name = "app.chat.messaging.rabbit.enabled", havingValue = "false")
public class LocalChatDeliveryListener {

    private static final Duration RETRY_DELAY = Duration.ofMinutes(10);

    private final IChatPersistencePort IChatPersistencePort;
    private final ChatSseEmitterRegistry chatSseEmitterRegistry;

    public LocalChatDeliveryListener(
            IChatPersistencePort IChatPersistencePort,
            ChatSseEmitterRegistry chatSseEmitterRegistry
    ) {
        this.IChatPersistencePort = IChatPersistencePort;
        this.chatSseEmitterRegistry = chatSseEmitterRegistry;
    }

    @Async("chatTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(ChatAssistantDeliveryQueuedEvent event) {
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
}
