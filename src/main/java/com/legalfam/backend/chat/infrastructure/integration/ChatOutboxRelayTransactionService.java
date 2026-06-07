package com.legalfam.backend.chat.infrastructure.integration;

import com.legalfam.backend.chat.application.port.out.ChatPersistencePort;
import com.legalfam.backend.chat.domain.model.ChatOutboxEvent;
import com.legalfam.backend.chat.domain.model.ChatOutboxEventStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatOutboxRelayTransactionService {

    private final ChatPersistencePort chatPersistencePort;

    public ChatOutboxRelayTransactionService(ChatPersistencePort chatPersistencePort) {
        this.chatPersistencePort = chatPersistencePort;
    }

    @Transactional
    public List<ChatOutboxEvent> claimReadyEvents(Instant now, int batchSize, Duration retryDelay) {
        List<ChatOutboxEvent> events = chatPersistencePort.lockReadyOutboxEvents(now, batchSize);
        Instant nextAvailableAt = now.plus(retryDelay);
        for (ChatOutboxEvent event : events) {
            if (event.getStatus() == ChatOutboxEventStatus.READ) {
                continue;
            }
            event.setAvailableAt(nextAvailableAt);
            event.setUpdatedAt(now);
            chatPersistencePort.saveOutboxEvent(event);
        }
        return events;
    }

    @Transactional
    public void recordPublishSuccess(UUID aggregateId, Instant now) {
        chatPersistencePort.findOutboxEventByAggregateIdForUpdate(aggregateId)
                .filter(event -> event.getStatus() != ChatOutboxEventStatus.READ)
                .ifPresent(event -> {
                    event.setLastError(null);
                    event.setUpdatedAt(now);
                    chatPersistencePort.saveOutboxEvent(event);
                });
    }

    @Transactional
    public void recordPublishFailure(UUID aggregateId, Instant now, Duration retryDelay, String errorMessage) {
        chatPersistencePort.findOutboxEventByAggregateIdForUpdate(aggregateId)
                .filter(event -> event.getStatus() != ChatOutboxEventStatus.READ)
                .ifPresent(event -> {
                    event.setStatus(ChatOutboxEventStatus.PENDING);
                    event.setAvailableAt(now.plus(retryDelay));
                    event.setLastError(errorMessage);
                    event.setUpdatedAt(now);
                    chatPersistencePort.saveOutboxEvent(event);
                });
    }
}
