package com.legalfam.backend.chat.infrastructure.integration;

import com.legalfam.backend.chat.application.event.ChatAssistantDeliveryQueuedEvent;
import com.legalfam.backend.chat.application.port.out.ChatPersistencePort;
import com.legalfam.backend.chat.domain.model.ChatOutboxEvent;
import com.legalfam.backend.chat.domain.model.ChatOutboxEventStatus;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@ConditionalOnProperty(name = "app.chat.messaging.rabbit.enabled", havingValue = "false")
public class ChatDeliveryLocalAsyncProcessor {

    private static final Duration RETRY_DELAY = Duration.ofMinutes(10);

    private final ChatPersistencePort chatPersistencePort;
    private final com.legalfam.backend.chat.infrastructure.sse.ChatSseEmitterService chatSseEmitterService;

    public ChatDeliveryLocalAsyncProcessor(
            ChatPersistencePort chatPersistencePort,
            com.legalfam.backend.chat.infrastructure.sse.ChatSseEmitterService chatSseEmitterService
    ) {
        this.chatPersistencePort = chatPersistencePort;
        this.chatSseEmitterService = chatSseEmitterService;
    }

    @Async("chatTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional
    public void process(ChatAssistantDeliveryQueuedEvent event) {
        ChatOutboxEvent outboxEvent = chatPersistencePort.findOutboxEventByAggregateIdForUpdate(event.assistantMessageId())
                .orElse(null);
        if (outboxEvent == null || outboxEvent.getStatus() == ChatOutboxEventStatus.READ) {
            return;
        }

        Instant now = Instant.now();
        boolean delivered = chatSseEmitterService.dispatchAssistantMessage(event.userId(), event.chatSessionId(), event.event());
        outboxEvent.setAttemptCount(outboxEvent.getAttemptCount() + 1);
        outboxEvent.setAvailableAt(now.plus(RETRY_DELAY));
        outboxEvent.setStatus(delivered ? ChatOutboxEventStatus.PUBLISHED : ChatOutboxEventStatus.PENDING);
        outboxEvent.setPublishedAt(delivered ? now : outboxEvent.getPublishedAt());
        outboxEvent.setLastError(delivered ? null : "No active SSE subscriber available");
        outboxEvent.setUpdatedAt(now);
        chatPersistencePort.saveOutboxEvent(outboxEvent);
    }
}
