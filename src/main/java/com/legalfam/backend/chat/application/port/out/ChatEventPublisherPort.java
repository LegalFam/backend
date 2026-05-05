package com.legalfam.backend.chat.application.port.out;

import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;

public interface ChatEventPublisherPort {
    void publishMessageQueued(ChatMessageQueuedEvent event);
}
