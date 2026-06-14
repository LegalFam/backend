package com.legalfam.backend.chat.application.port.out;

import com.legalfam.backend.chat.application.event.ChatAssistantDeliveryQueuedEvent;

public interface IChatEventPublisherPort {
    void publishAssistantDelivery(ChatAssistantDeliveryQueuedEvent event);
}
