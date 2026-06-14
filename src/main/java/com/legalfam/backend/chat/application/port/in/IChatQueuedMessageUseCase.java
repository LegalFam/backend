package com.legalfam.backend.chat.application.port.in;

import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;

public interface IChatQueuedMessageUseCase {
    void process(ChatMessageQueuedEvent event);
}
