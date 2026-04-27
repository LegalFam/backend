package com.legalfam.backend.chat.infrastructure.adapter.events;

import com.legalfam.backend.chat.application.port.out.ChatEventPublisherPort;
import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.chat.messaging.rabbit.enabled", havingValue = "false")
public class SpringChatEventPublisherAdapter implements ChatEventPublisherPort {

    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringChatEventPublisherAdapter(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publishMessageQueued(UUID chatSessionId, String userMessageInput) {
        applicationEventPublisher.publishEvent(new ChatMessageQueuedEvent(chatSessionId, userMessageInput));
    }
}
