package com.legalfam.backend.chat.application.port.out;

import com.legalfam.backend.chat.application.event.ChatAssistantDeliveryQueuedEvent;

public interface ChatOutboxPort {
    void enqueueAssistantDelivery(ChatAssistantDeliveryQueuedEvent event);
}
