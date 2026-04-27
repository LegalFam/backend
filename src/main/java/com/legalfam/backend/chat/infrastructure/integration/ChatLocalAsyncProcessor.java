package com.legalfam.backend.chat.infrastructure.integration;

import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@ConditionalOnProperty(name = "app.chat.messaging.rabbit.enabled", havingValue = "false")
public class ChatLocalAsyncProcessor {

    private final ChatMessageEventProcessor chatMessageEventProcessor;

    public ChatLocalAsyncProcessor(ChatMessageEventProcessor chatMessageEventProcessor) {
        this.chatMessageEventProcessor = chatMessageEventProcessor;
    }

    @Async("chatTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void process(ChatMessageQueuedEvent event) {
        chatMessageEventProcessor.process(event);
    }
}
