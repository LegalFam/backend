package com.legalfam.backend.chat.infrastructure.adapter.events;

import com.legalfam.backend.chat.application.event.ChatAssistantDeliveryQueuedEvent;
import com.legalfam.backend.chat.application.port.out.IChatEventPublisherPort;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "app.chat.messaging.rabbit.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitChatEventPublisherAdapter implements IChatEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(RabbitChatEventPublisherAdapter.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String exchange;
    private final String assistantDeliveryRoutingKey;
    private final long confirmTimeoutMs;

    public RabbitChatEventPublisherAdapter(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${app.chat.messaging.rabbit.exchange}") String exchange,
            @Value("${app.chat.messaging.rabbit.routing-key.assistant-delivery}") String assistantDeliveryRoutingKey,
            @Value("${app.chat.messaging.rabbit.publisher.confirm-timeout-ms:5000}") long confirmTimeoutMs
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.exchange = exchange;
        this.assistantDeliveryRoutingKey = assistantDeliveryRoutingKey;
        this.confirmTimeoutMs = confirmTimeoutMs;
    }

    @Override
    public void publishAssistantDelivery(ChatAssistantDeliveryQueuedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            CorrelationData correlationData = new CorrelationData(event.assistantMessageId().toString());
            rabbitTemplate.convertAndSend(exchange, assistantDeliveryRoutingKey, payload, correlationData);

            CorrelationData.Confirm confirm = correlationData.getFuture().get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
            ReturnedMessage returnedMessage = correlationData.getReturned();
            if (returnedMessage != null) {
                throw new IllegalStateException("RabbitMQ returned assistant delivery message");
            }
            if (confirm == null || !confirm.isAck()) {
                String reason = confirm == null ? "missing broker confirm" : confirm.getReason();
                throw new IllegalStateException("RabbitMQ did not ack assistant delivery message: " + reason);
            }

            log.debug("Published assistant delivery event to RabbitMQ: exchange={}, routingKey={}, sessionId={}, messageId={}",
                    exchange, assistantDeliveryRoutingKey, event.chatSessionId(), event.assistantMessageId());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to publish assistant delivery event to RabbitMQ", ex);
        }
    }
}
