package com.legalfam.backend.chat.application.port.out;

import java.util.UUID;

public interface ChatEventPublisherPort {
    void publishMessageQueued(UUID chatSessionId, UUID userMessageId, String userMessageInput);
}
