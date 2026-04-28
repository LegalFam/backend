package com.legalfam.backend.chat.application.dto;

import com.legalfam.backend.chat.application.event.ChatAssistantErrorEvent;
import java.util.UUID;

public record ChatAssistantErrorDispatch(
        UUID userId,
        UUID chatSessionId,
        ChatAssistantErrorEvent event
) {
}
