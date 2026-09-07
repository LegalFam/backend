package com.legalfam.backend.chat.application.event;

import com.legalfam.backend.chat.application.dto.ChatPreviousMessage;
import com.legalfam.backend.chat.domain.model.ChatLanguage;
import java.util.List;
import java.util.UUID;

public record ChatMessageQueuedEvent(
        UUID chatSessionId,
        UUID userMessageId,
        String userMessageInput,
        List<ChatPreviousMessage> previousMessages,
        String language
) {
    public ChatMessageQueuedEvent {
        previousMessages = previousMessages == null ? List.of() : List.copyOf(previousMessages);
        // Este record viaja serializado por RabbitMQ. Un evento encolado antes del despliegue
        // que introdujo el idioma no trae el campo, y debe seguir siendo procesable: cae a
        // espanol en lugar de fallar.
        language = ChatLanguage.fromCode(language).code();
    }

    public ChatLanguage resolvedLanguage() {
        return ChatLanguage.fromCode(language);
    }
}
