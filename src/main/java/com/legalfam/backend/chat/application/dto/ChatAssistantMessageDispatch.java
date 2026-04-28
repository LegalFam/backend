package com.legalfam.backend.chat.application.dto;

import com.legalfam.backend.chat.application.event.ChatAssistantMessageEvent;
import java.util.UUID;

public record ChatAssistantMessageDispatch(
        UUID userId,
        UUID chatSessionId,
        ChatAssistantMessageEvent event
) {
}
