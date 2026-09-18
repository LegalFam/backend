package com.legalfam.backend.chat.application.port.out;

import com.legalfam.backend.chat.application.event.ChatAssistantDeliveryQueuedEvent;
import com.legalfam.backend.chat.application.event.ChatAssistantErrorEvent;
import com.legalfam.backend.chat.application.event.ChatAssistantMessageEvent;
import java.util.UUID;

public interface IChatAssistantDeliveryPort {
    boolean dispatchAssistantMessage(UUID userId, UUID sessionId, ChatAssistantMessageEvent event);

    boolean dispatchAssistantError(UUID userId, UUID sessionId, ChatAssistantErrorEvent event);

    default boolean dispatch(ChatAssistantDeliveryQueuedEvent delivery) {
        if (delivery.error() != null) {
            return dispatchAssistantError(delivery.userId(), delivery.chatSessionId(), delivery.error());
        }
        return dispatchAssistantMessage(delivery.userId(), delivery.chatSessionId(), delivery.event());
    }
}
