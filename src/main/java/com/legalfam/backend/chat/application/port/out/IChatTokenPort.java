package com.legalfam.backend.chat.application.port.out;

import java.util.UUID;

public interface IChatTokenPort {
    void consumeChatTokensForAssistantResult(UUID userId, UUID chatMessageId, int tokenCost);
}
