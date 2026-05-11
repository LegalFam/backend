package com.legalfam.backend.chat.application.port.out;

import com.legalfam.backend.chat.application.event.ChatAssistantDeliveryQueuedEvent;

public interface ChatEventPublisherPort {
    void publishAssistantDelivery(ChatAssistantDeliveryQueuedEvent event);
}
