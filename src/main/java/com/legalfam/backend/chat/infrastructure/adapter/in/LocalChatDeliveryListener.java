package com.legalfam.backend.chat.infrastructure.adapter.in;

import com.legalfam.backend.chat.application.event.ChatAssistantDeliveryQueuedEvent;
import com.legalfam.backend.chat.application.port.out.IChatAssistantDeliveryPort;
import com.legalfam.backend.chat.application.port.out.IChatPersistencePort;
import com.legalfam.backend.chat.domain.model.ChatOutboxEvent;
import com.legalfam.backend.chat.infrastructure.config.ChatOutboxRelayProperties;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(LocalChatDeliveryListener.class);

    private final IChatPersistencePort IChatPersistencePort;
    private final IChatAssistantDeliveryPort IChatAssistantDeliveryPort;
    private final Duration retryDelay;

    public LocalChatDeliveryListener(
            IChatPersistencePort IChatPersistencePort,
            IChatAssistantDeliveryPort IChatAssistantDeliveryPort,
            ChatOutboxRelayProperties properties
    ) {
        this.IChatPersistencePort = IChatPersistencePort;
        this.IChatAssistantDeliveryPort = IChatAssistantDeliveryPort;
        this.retryDelay = Duration.ofMillis(properties.safeRetryDelayMs());
    }

    @Async("chatTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(ChatAssistantDeliveryQueuedEvent event) {
        ChatOutboxEvent outboxEvent = IChatPersistencePort.findOutboxEventByAggregateIdForUpdate(event.assistantMessageId())
                .orElse(null);
        if (outboxEvent == null || outboxEvent.isRead()) {
            return;
        }

        Instant now = Instant.now();
        boolean delivered = IChatAssistantDeliveryPort.dispatch(event);
        outboxEvent.recordDeliveryAttempt(delivered, now.plus(retryDelay), "No active SSE subscriber available", now);
        IChatPersistencePort.saveOutboxEvent(outboxEvent);
        log.info("[FAULT-INJECTION] delivery_attempt messageId={} delivered={} status={} attemptCount={} availableAt={}",
                event.assistantMessageId(), delivered, outboxEvent.getStatus(), outboxEvent.getAttemptCount(),
                outboxEvent.getAvailableAt());
    }
}
