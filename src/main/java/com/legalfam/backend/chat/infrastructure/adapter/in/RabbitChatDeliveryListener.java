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
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(name = "app.chat.messaging.rabbit.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitChatDeliveryListener {

    private static final Logger log = LoggerFactory.getLogger(RabbitChatDeliveryListener.class);

    private final ObjectMapper objectMapper;
    private final IChatPersistencePort IChatPersistencePort;
    private final IChatAssistantDeliveryPort IChatAssistantDeliveryPort;
    private final Duration retryDelay;

    public RabbitChatDeliveryListener(
            ObjectMapper objectMapper,
            IChatPersistencePort IChatPersistencePort,
            IChatAssistantDeliveryPort IChatAssistantDeliveryPort,
            ChatOutboxRelayProperties properties
    ) {
        this.objectMapper = objectMapper;
        this.IChatPersistencePort = IChatPersistencePort;
        this.IChatAssistantDeliveryPort = IChatAssistantDeliveryPort;
        this.retryDelay = Duration.ofMillis(properties.safeRetryDelayMs());
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

    private ChatAssistantDeliveryQueuedEvent parseEvent(String payload) {
        try {
            return objectMapper.readValue(payload, ChatAssistantDeliveryQueuedEvent.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid assistant delivery event payload", ex);
        }
    }
}
