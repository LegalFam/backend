package com.legalfam.backend.chat.application.port.out;

import com.legalfam.backend.chat.application.event.ChatAssistantDeliveryQueuedEvent;

public interface IChatOutboxPort {
    void enqueueAssistantDelivery(ChatAssistantDeliveryQueuedEvent event);
}
