package com.legalfam.backend.chat.infrastructure.integration;

import com.legalfam.backend.chat.application.event.ChatAssistantDeliveryQueuedEvent;
import com.legalfam.backend.chat.application.port.out.ChatEventPublisherPort;
import com.legalfam.backend.chat.application.port.out.ChatPersistencePort;
import com.legalfam.backend.chat.domain.model.ChatOutboxEvent;
import com.legalfam.backend.chat.domain.model.ChatOutboxEventStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(name = "app.chat.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class ChatDeliveryRetryWorker {

    private static final Logger log = LoggerFactory.getLogger(ChatDeliveryRetryWorker.class);

    private final ChatPersistencePort chatPersistencePort;
    private final ChatEventPublisherPort chatEventPublisherPort;
    private final ObjectMapper objectMapper;
    private final int batchSize;
    private final Duration retryDelay;

    public ChatDeliveryRetryWorker(
            ChatPersistencePort chatPersistencePort,
            ChatEventPublisherPort chatEventPublisherPort,
            ObjectMapper objectMapper,
            @Value("${app.chat.outbox.relay.batch-size:50}") int batchSize,
            @Value("${app.chat.outbox.relay.retry-delay-ms:600000}") long retryDelayMs
    ) {
        this.chatPersistencePort = chatPersistencePort;
        this.chatEventPublisherPort = chatEventPublisherPort;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
        this.retryDelay = Duration.ofMillis(retryDelayMs);
    }

    @Scheduled(fixedDelayString = "${app.chat.outbox.relay.fixed-delay-ms:5000}")
    @Transactional
    public void relayReadyEvents() {
        Instant now = Instant.now();
        List<ChatOutboxEvent> events = chatPersistencePort.lockReadyOutboxEvents(now, batchSize);
        for (ChatOutboxEvent event : events) {
            relaySingleEvent(event, now);
        }
    }

    private void relaySingleEvent(ChatOutboxEvent event, Instant now) {
        if (event.getStatus() == ChatOutboxEventStatus.READ) {
            return;
        }

        try {
            ChatAssistantDeliveryQueuedEvent payload =
                    objectMapper.readValue(event.getPayload(), ChatAssistantDeliveryQueuedEvent.class);
            chatEventPublisherPort.publishAssistantDelivery(payload);

            event.setAvailableAt(now.plus(retryDelay));
            event.setLastError(null);
            event.setUpdatedAt(now);
            chatPersistencePort.saveOutboxEvent(event);
        } catch (Exception ex) {
            event.setStatus(ChatOutboxEventStatus.PENDING);
            event.setAvailableAt(now.plus(retryDelay));
            event.setLastError(truncateError(ex.getMessage()));
            event.setUpdatedAt(now);
            chatPersistencePort.saveOutboxEvent(event);

            log.warn("Failed to publish assistant delivery outbox event id={} aggregateId={} error={}",
                    event.getId(), event.getAggregateId(), truncateError(ex.getMessage()));
        }
    }

    private String truncateError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "Unknown delivery relay error";
        }
        return errorMessage.length() <= 1000 ? errorMessage : errorMessage.substring(0, 1000);
    }
}
