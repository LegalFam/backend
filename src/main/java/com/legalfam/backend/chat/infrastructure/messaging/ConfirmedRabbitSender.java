package com.legalfam.backend.chat.infrastructure.messaging;

import com.legalfam.backend.chat.infrastructure.config.ChatRabbitProperties;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Publica en el exchange del chat y solo vuelve cuando el broker confirmo el mensaje. */
@Component
@ConditionalOnProperty(name = "app.chat.messaging.rabbit.enabled", havingValue = "true", matchIfMissing = true)
public class ConfirmedRabbitSender {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String exchange;
    private final long confirmTimeoutMs;

    public ConfirmedRabbitSender(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper, ChatRabbitProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.exchange = properties.exchangeName();
        this.confirmTimeoutMs = properties.safePublisherConfirmTimeoutMs();
    }

    public void send(String routingKey, Object event, String correlationId) {
        try {
            CorrelationData correlationData = new CorrelationData(correlationId);
            rabbitTemplate.convertAndSend(exchange, routingKey, objectMapper.writeValueAsString(event), correlationData);

            CorrelationData.Confirm confirm = correlationData.getFuture().get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
            ReturnedMessage returnedMessage = correlationData.getReturned();
            if (returnedMessage != null) {
                throw new IllegalStateException("RabbitMQ returned the message routed with " + routingKey);
            }
            if (!confirm.ack()) {
                throw new IllegalStateException("RabbitMQ did not ack the message: " + confirm.reason());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to publish to RabbitMQ with routing key " + routingKey, ex);
        }
    }
}
