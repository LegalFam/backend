package com.legalfam.backend.chat.infrastructure.integration;

import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(name = "app.chat.messaging.rabbit.enabled", havingValue = "true", matchIfMissing = true)
public class ChatAsyncProcessor {

    private final ObjectMapper objectMapper;
    private final ChatMessageEventProcessor chatMessageEventProcessor;

    public ChatAsyncProcessor(
            ObjectMapper objectMapper,
            ChatMessageEventProcessor chatMessageEventProcessor
    ) {
        this.objectMapper = objectMapper;
        this.chatMessageEventProcessor = chatMessageEventProcessor;
    }

    @RabbitListener(queues = "${app.chat.messaging.rabbit.queue.chat-message-queued}")
    public void process(String payload) {
        ChatMessageQueuedEvent event = parseEvent(payload);
        chatMessageEventProcessor.process(event);
    }

    private ChatMessageQueuedEvent parseEvent(String payload) {
        try {
            return objectMapper.readValue(payload, ChatMessageQueuedEvent.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid chat event payload", ex);
        }
    }
}
