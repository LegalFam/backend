package com.legalfam.backend.chat.infrastructure.worker;

import com.legalfam.backend.chat.application.event.ChatAssistantDeliveryQueuedEvent;
import com.legalfam.backend.chat.application.port.out.IChatEventPublisherPort;
import com.legalfam.backend.chat.domain.model.ChatOutboxEvent;
import com.legalfam.backend.chat.infrastructure.config.ChatOutboxRelayProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.legalfam.backend.chat.infrastructure.adapter.out.ChatOutboxRelayTransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(name = "app.chat.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class ChatDeliveryRetryWorker {

    private static final Logger log = LoggerFactory.getLogger(ChatDeliveryRetryWorker.class);

    private final ChatOutboxRelayTransactionService relayTransactionService;
    private final IChatEventPublisherPort IChatEventPublisherPort;
    private final ObjectMapper objectMapper;
    private final int batchSize;
    private final Duration retryDelay;

    public ChatDeliveryRetryWorker(
            ChatOutboxRelayTransactionService relayTransactionService,
            IChatEventPublisherPort IChatEventPublisherPort,
            ObjectMapper objectMapper,
            ChatOutboxRelayProperties properties
    ) {
        this.relayTransactionService = relayTransactionService;
        this.IChatEventPublisherPort = IChatEventPublisherPort;
        this.objectMapper = objectMapper;
        this.batchSize = properties.safeBatchSize();
        this.retryDelay = Duration.ofMillis(properties.safeRetryDelayMs());
    }

    @Scheduled(fixedDelayString = "${app.chat.outbox.relay.fixed-delay-ms:5000}")
    public void relayReadyEvents() {
        Instant now = Instant.now();
        List<ChatOutboxEvent> events = relayTransactionService.claimReadyEvents(now, batchSize, retryDelay);
        for (ChatOutboxEvent event : events) {
            relaySingleEvent(event);
        }
    }

    private void relaySingleEvent(ChatOutboxEvent event) {
        if (event.isRead()) {
            return;
        }

        UUID aggregateId = event.getAggregateId();
        Instant now = Instant.now();
        try {
            ChatAssistantDeliveryQueuedEvent payload =
                    objectMapper.readValue(event.getPayload(), ChatAssistantDeliveryQueuedEvent.class);
            IChatEventPublisherPort.publishAssistantDelivery(payload);
            relayTransactionService.recordPublishSuccess(aggregateId, now);
        } catch (Exception ex) {
            String errorMessage = truncateError(ex.getMessage());
            relayTransactionService.recordPublishFailure(aggregateId, now, retryDelay, errorMessage);
            log.warn("Failed to publish assistant delivery outbox event id={} aggregateId={} error={}",
                    event.getId(), aggregateId, errorMessage);
        }
    }

    private String truncateError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "Unknown delivery relay error";
        }
        return errorMessage.length() <= 1000 ? errorMessage : errorMessage.substring(0, 1000);
    }
}
