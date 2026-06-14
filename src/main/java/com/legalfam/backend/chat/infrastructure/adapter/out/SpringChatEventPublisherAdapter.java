package com.legalfam.backend.chat.infrastructure.adapter.out;

import com.legalfam.backend.chat.application.event.ChatAssistantDeliveryQueuedEvent;
import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import com.legalfam.backend.chat.application.port.out.IChatEventPublisherPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.chat.messaging.rabbit.enabled", havingValue = "false")
public class SpringChatEventPublisherAdapter implements IChatEventPublisherPort {

    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringChatEventPublisherAdapter(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publishMessageQueued(ChatMessageQueuedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    @Override
    public void publishAssistantDelivery(ChatAssistantDeliveryQueuedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
