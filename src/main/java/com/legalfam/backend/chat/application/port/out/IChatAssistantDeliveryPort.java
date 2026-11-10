package com.legalfam.backend.chat.application.port.out;

import com.legalfam.backend.chat.application.event.ChatAssistantDeliveryQueuedEvent;
import com.legalfam.backend.chat.application.event.ChatAssistantDraftEvent;
import com.legalfam.backend.chat.application.event.ChatAssistantErrorEvent;
import com.legalfam.backend.chat.application.event.ChatAssistantMessageEvent;
import java.util.UUID;

public interface IChatAssistantDeliveryPort {
    boolean dispatchAssistantMessage(UUID userId, UUID sessionId, ChatAssistantMessageEvent event);

    boolean dispatchAssistantError(UUID userId, UUID sessionId, ChatAssistantErrorEvent event);

    /**
     * Entrega un borrador en vivo a quien este suscrito a la sesion en esta instancia. Es de
     * mejor esfuerzo: no pasa por el outbox ni se reintenta, porque el mensaje definitivo
     * llega igual por la via normal.
     */
    default boolean dispatchAssistantDraft(ChatAssistantDraftEvent event) {
        return false;
    }

    default boolean dispatch(ChatAssistantDeliveryQueuedEvent delivery) {
        if (delivery.error() != null) {
            return dispatchAssistantError(delivery.userId(), delivery.chatSessionId(), delivery.error());
        }
        return dispatchAssistantMessage(delivery.userId(), delivery.chatSessionId(), delivery.event());
    }
}
