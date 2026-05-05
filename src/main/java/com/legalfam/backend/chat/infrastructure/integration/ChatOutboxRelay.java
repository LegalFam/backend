package com.legalfam.backend.chat.infrastructure.integration;

import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import com.legalfam.backend.chat.application.port.in.ChatAssistantPersistenceUseCase;
import com.legalfam.backend.chat.application.port.out.ChatEventPublisherPort;
import com.legalfam.backend.chat.application.port.out.ChatPersistencePort;
import com.legalfam.backend.chat.domain.model.ChatOutboxEvent;
import com.legalfam.backend.chat.domain.model.ChatOutboxEventStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(name = "app.chat.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class ChatOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(ChatOutboxRelay.class);
    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            Duration.ofMinutes(30),
            Duration.ofMinutes(60)
    );

    private final ChatPersistencePort chatPersistencePort;
    private final ChatEventPublisherPort chatEventPublisherPort;
    private final ChatAssistantPersistenceUseCase chatAssistantPersistenceUseCase;
    private final ObjectMapper objectMapper;
    private final int batchSize;

    public ChatOutboxRelay(
            ChatPersistencePort chatPersistencePort,
            ChatEventPublisherPort chatEventPublisherPort,
            ChatAssistantPersistenceUseCase chatAssistantPersistenceUseCase,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Value("${app.chat.outbox.relay.batch-size:50}") int batchSize
    ) {
        this.chatPersistencePort = chatPersistencePort;
        this.chatEventPublisherPort = chatEventPublisherPort;
        this.chatAssistantPersistenceUseCase = chatAssistantPersistenceUseCase;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.chat.outbox.relay.fixed-delay-ms:5000}")
    @Transactional
    public void relayReadyEvents() {
        relayReadyBatch();
    }

    public void relayReadyBatch() {
        Instant now = Instant.now();
        List<ChatOutboxEvent> events = chatPersistencePort.lockReadyOutboxEvents(now, batchSize);
        for (ChatOutboxEvent event : events) {
            relaySingleEvent(event, now);
        }
    }

    private void relaySingleEvent(ChatOutboxEvent event, Instant now) {
        if (!now.isBefore(event.getExpiresAt())) {
            markDead(event, now, "OUTBOX_EXPIRED", "Chat processing expired before the event could be published.");
            return;
        }

        try {
            ChatMessageQueuedEvent payload = objectMapper.readValue(event.getPayload(), ChatMessageQueuedEvent.class);
            chatEventPublisherPort.publishMessageQueued(payload);

            event.setStatus(ChatOutboxEventStatus.PUBLISHED);
            event.setAttemptCount(event.getAttemptCount() + 1);
            event.setPublishedAt(now);
            event.setLastError(null);
            event.setUpdatedAt(now);
            chatPersistencePort.saveOutboxEvent(event);
        } catch (Exception ex) {
            int nextAttemptCount = event.getAttemptCount() + 1;
            event.setAttemptCount(nextAttemptCount);
            event.setLastError(truncateError(ex.getMessage()));
            event.setUpdatedAt(now);

            if (!now.isBefore(event.getExpiresAt())) {
                markDead(event, now, "OUTBOX_EXPIRED", "Chat processing expired before the event could be published.");
                return;
            }

            event.setStatus(ChatOutboxEventStatus.FAILED);
            event.setAvailableAt(now.plus(resolveRetryDelay(nextAttemptCount)));
            chatPersistencePort.saveOutboxEvent(event);

            log.warn("Failed to publish chat outbox event id={} aggregateId={} attempt={} error={}",
                    event.getId(), event.getAggregateId(), nextAttemptCount, truncateError(ex.getMessage()));
        }
    }

    private void markDead(ChatOutboxEvent event, Instant now, String errorCode, String errorMessage) {
        event.setStatus(ChatOutboxEventStatus.DEAD);
        event.setAvailableAt(now);
        event.setUpdatedAt(now);
        event.setLastError(errorMessage);
        chatPersistencePort.saveOutboxEvent(event);
        chatAssistantPersistenceUseCase.expireUserMessage(event.getAggregateId(), errorCode, errorMessage);
    }

    private Duration resolveRetryDelay(int attemptCount) {
        int index = Math.max(0, Math.min(attemptCount - 1, RETRY_DELAYS.size() - 1));
        return RETRY_DELAYS.get(index);
    }

    private String truncateError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "Unknown outbox relay error";
        }
        return errorMessage.length() <= 1000 ? errorMessage : errorMessage.substring(0, 1000);
    }
}
