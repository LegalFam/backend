package com.legalfam.backend.chat.infrastructure.adapter.out;

import com.legalfam.backend.chat.application.event.ChatAssistantDeliveryQueuedEvent;
import com.legalfam.backend.chat.application.port.out.IChatOutboxPort;
import com.legalfam.backend.chat.application.port.out.IChatPersistencePort;
import com.legalfam.backend.chat.domain.model.ChatOutboxEvent;
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
            event.registerAssistantDelivery(
                    deliveryEvent.assistantMessageId(),
                    deliveryEvent.chatSessionId(),
                    objectMapper.writeValueAsString(deliveryEvent),
                    now
            );

            IChatPersistencePort.saveOutboxEvent(event);
            applicationEventPublisher.publishEvent(deliveryEvent);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to register assistant delivery in chat outbox", ex);
        }
    }
}
