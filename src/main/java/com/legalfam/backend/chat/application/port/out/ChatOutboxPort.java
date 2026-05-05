package com.legalfam.backend.chat.application.port.out;

import java.util.UUID;

public interface ChatOutboxPort {
    void enqueueMessageQueued(UUID chatSessionId, UUID userMessageId, String userMessageInput);
}
