package com.legalfam.backend.chat.application.port.out;

import com.legalfam.backend.chat.application.event.ChatAssistantErrorEvent;
import com.legalfam.backend.chat.application.event.ChatAssistantMessageEvent;
import java.util.UUID;

public interface IChatAssistantDeliveryPort {
    boolean dispatchAssistantMessage(UUID userId, UUID sessionId, ChatAssistantMessageEvent event);

    boolean dispatchAssistantError(UUID userId, UUID sessionId, ChatAssistantErrorEvent event);
}
