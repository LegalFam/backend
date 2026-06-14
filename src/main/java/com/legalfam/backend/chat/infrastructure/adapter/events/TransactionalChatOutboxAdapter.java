package com.legalfam.backend.chat.infrastructure.adapter.events;

import com.legalfam.backend.chat.application.event.ChatAssistantDeliveryQueuedEvent;
import com.legalfam.backend.chat.application.port.out.IChatOutboxPort;
import com.legalfam.backend.chat.application.port.out.IChatPersistencePort;
import com.legalfam.backend.chat.domain.model.ChatOutboxEvent;
import com.legalfam.backend.chat.domain.model.ChatOutboxEventStatus;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class TransactionalChatOutboxAdapter implements IChatOutboxPort {

    private final IChatPersistencePort IChatPersistencePort;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    public TransactionalChatOutboxAdapter(
            IChatPersistencePort IChatPersistencePort,
            ObjectMapper objectMapper,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.IChatPersistencePort = IChatPersistencePort;
        this.objectMapper = objectMapper;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void enqueueAssistantDelivery(ChatAssistantDeliveryQueuedEvent deliveryEvent) {
        try {
            Instant now = Instant.now();

            ChatOutboxEvent event = IChatPersistencePort.findOutboxEventByAggregateId(deliveryEvent.assistantMessageId())
                    .orElseGet(ChatOutboxEvent::new);
            event.setEventType(ChatOutboxEvent.ASSISTANT_DELIVERY_EVENT_TYPE);
            event.setAggregateId(deliveryEvent.assistantMessageId());
            event.setChatSessionId(deliveryEvent.chatSessionId());
            event.setPayload(objectMapper.writeValueAsString(deliveryEvent));
            if (event.getStatus() == null || event.getStatus() == ChatOutboxEventStatus.READ) {
                event.setStatus(ChatOutboxEventStatus.PENDING);
            }
            if (event.getCreatedAt() == null) {
                event.setCreatedAt(now);
            }
            event.setAvailableAt(now);
            event.setUpdatedAt(now);
            if (event.getPublishedAt() == null) {
                event.setAttemptCount(0);
            }

            IChatPersistencePort.saveOutboxEvent(event);
            applicationEventPublisher.publishEvent(deliveryEvent);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to register assistant delivery in chat outbox", ex);
        }
    }
}
