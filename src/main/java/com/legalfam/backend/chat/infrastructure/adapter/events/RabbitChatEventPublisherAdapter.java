package com.legalfam.backend.chat.infrastructure.adapter.events;

import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import com.legalfam.backend.chat.application.port.out.ChatEventPublisherPort;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "app.chat.messaging.rabbit.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitChatEventPublisherAdapter implements ChatEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(RabbitChatEventPublisherAdapter.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String exchange;
    private final String chatMessageQueuedRoutingKey;

    public RabbitChatEventPublisherAdapter(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${app.chat.messaging.rabbit.exchange}") String exchange,
            @Value("${app.chat.messaging.rabbit.routing-key.chat-message-queued}") String chatMessageQueuedRoutingKey
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.exchange = exchange;
        this.chatMessageQueuedRoutingKey = chatMessageQueuedRoutingKey;
    }

    @Override
    public void publishMessageQueued(UUID chatSessionId, String userMessageInput) {
        try {
            ChatMessageQueuedEvent event = new ChatMessageQueuedEvent(chatSessionId, userMessageInput);
            String payload = objectMapper.writeValueAsString(event);
            rabbitTemplate.convertAndSend(exchange, chatMessageQueuedRoutingKey, payload);
            log.debug("Published chat message event to RabbitMQ: exchange={}, routingKey={}, sessionId={}",
                    exchange, chatMessageQueuedRoutingKey, chatSessionId);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to publish chat message event to RabbitMQ", ex);
        }
    }
}
