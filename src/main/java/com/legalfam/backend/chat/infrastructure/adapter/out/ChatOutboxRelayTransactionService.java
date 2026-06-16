package com.legalfam.backend.chat.infrastructure.adapter.out;

import com.legalfam.backend.chat.application.port.out.IChatPersistencePort;
import com.legalfam.backend.chat.domain.model.ChatOutboxEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatOutboxRelayTransactionService {

    private final IChatPersistencePort IChatPersistencePort;

    public ChatOutboxRelayTransactionService(IChatPersistencePort IChatPersistencePort) {
        this.IChatPersistencePort = IChatPersistencePort;
    }

    @Transactional
    public List<ChatOutboxEvent> claimReadyEvents(Instant now, int batchSize, Duration retryDelay) {
        List<ChatOutboxEvent> events = IChatPersistencePort.lockReadyOutboxEvents(now, batchSize);
        Instant nextAvailableAt = now.plus(retryDelay);
        for (ChatOutboxEvent event : events) {
            if (event.reserveForRelay(nextAvailableAt, now)) {
                IChatPersistencePort.saveOutboxEvent(event);
            }
        }
        return events;
    }

    @Transactional
    public void recordPublishSuccess(UUID aggregateId, Instant now) {
        IChatPersistencePort.findOutboxEventByAggregateIdForUpdate(aggregateId)
                .filter(event -> !event.isRead())
                .ifPresent(event -> {
                    if (event.recordRelaySuccess(now)) {
                        IChatPersistencePort.saveOutboxEvent(event);
                    }
                });
    }

    @Transactional
    public void recordPublishFailure(UUID aggregateId, Instant now, Duration retryDelay, String errorMessage) {
        IChatPersistencePort.findOutboxEventByAggregateIdForUpdate(aggregateId)
                .filter(event -> !event.isRead())
                .ifPresent(event -> {
                    if (event.recordRelayFailure(now.plus(retryDelay), errorMessage, now)) {
                        IChatPersistencePort.saveOutboxEvent(event);
                    }
                });
    }
}
