package com.legalfam.backend.chat.application.port.out;

import com.legalfam.backend.chat.application.event.ChatAssistantDeliveryQueuedEvent;
import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;

public interface IChatEventPublisherPort {
    void publishMessageQueued(ChatMessageQueuedEvent event);

    void publishAssistantDelivery(ChatAssistantDeliveryQueuedEvent event);
}
