package com.legalfam.backend.chat.infrastructure.adapter.in;

import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class LocalChatQueuedMessageListener {

    private final ChatQueuedMessageEventHandler chatQueuedMessageEventHandler;

    public LocalChatQueuedMessageListener(ChatQueuedMessageEventHandler chatQueuedMessageEventHandler) {
        this.chatQueuedMessageEventHandler = chatQueuedMessageEventHandler;
    }

    @Async("chatTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void process(ChatMessageQueuedEvent event) {
        chatQueuedMessageEventHandler.process(event);
    }
}
