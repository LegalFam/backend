package com.legalfam.backend.chat.infrastructure.adapter.in;

import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import com.legalfam.backend.chat.application.port.in.IChatQueuedMessageUseCase;
import org.springframework.stereotype.Service;

@Service
public class ChatQueuedMessageEventHandler {

    private final IChatQueuedMessageUseCase IChatQueuedMessageUseCase;

    public ChatQueuedMessageEventHandler(IChatQueuedMessageUseCase IChatQueuedMessageUseCase) {
        this.IChatQueuedMessageUseCase = IChatQueuedMessageUseCase;
    }

    public void process(ChatMessageQueuedEvent event) {
        IChatQueuedMessageUseCase.process(event);
    }
}
