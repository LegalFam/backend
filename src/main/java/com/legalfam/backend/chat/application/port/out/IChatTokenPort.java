package com.legalfam.backend.chat.application.port.out;

import java.util.UUID;

public interface IChatTokenPort {
    void consumeChatToken(UUID userId, UUID chatMessageId);

    void refundChatToken(UUID chatMessageId);
}
