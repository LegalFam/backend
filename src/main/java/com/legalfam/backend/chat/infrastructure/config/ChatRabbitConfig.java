package com.legalfam.backend.chat.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
@ConditionalOnProperty(name = "app.chat.messaging.rabbit.enabled", havingValue = "true", matchIfMissing = true)
public class ChatRabbitConfig {

    @Bean
    public TopicExchange chatEventsExchange(
            @Value("${app.chat.messaging.rabbit.exchange}") String exchangeName
    ) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public DirectExchange chatDeadLetterExchange(
            @Value("${app.chat.messaging.rabbit.exchange.dlx}") String dlxName
    ) {
        return new DirectExchange(dlxName, true, false);
    }

    @Bean
    public Queue chatMessageQueuedQueue(
            @Value("${app.chat.messaging.rabbit.queue.chat-message-queued}") String queueName,
            @Value("${app.chat.messaging.rabbit.exchange.dlx}") String deadLetterExchange,
            @Value("${app.chat.messaging.rabbit.routing-key.dlq}") String deadLetterRoutingKey,
            @Value("${app.chat.messaging.rabbit.queue.ttl-ms:10800000}") long messageTtlMs
    ) {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey(deadLetterRoutingKey)
                .ttl((int) messageTtlMs)
                .withArgument("x-queue-type", "quorum")
                .build();
    }

    @Bean
    public Queue chatMessageQueuedDeadLetterQueue(
            @Value("${app.chat.messaging.rabbit.queue.dlq}") String deadLetterQueueName
    ) {
        return QueueBuilder.durable(deadLetterQueueName).build();
    }

    @Bean
    public Binding chatMessageQueuedBinding(
            @Qualifier("chatMessageQueuedQueue") Queue chatMessageQueuedQueue,
            TopicExchange chatEventsExchange,
            @Value("${app.chat.messaging.rabbit.routing-key.chat-message-queued}") String routingKey
    ) {
        return BindingBuilder.bind(chatMessageQueuedQueue).to(chatEventsExchange).with(routingKey);
    }

    @Bean
    public Binding chatMessageQueuedDeadLetterBinding(
            @Qualifier("chatMessageQueuedDeadLetterQueue") Queue chatMessageQueuedDeadLetterQueue,
            DirectExchange chatDeadLetterExchange,
            @Value("${app.chat.messaging.rabbit.routing-key.dlq}") String deadLetterRoutingKey
    ) {
        return BindingBuilder.bind(chatMessageQueuedDeadLetterQueue)
                .to(chatDeadLetterExchange)
                .with(deadLetterRoutingKey);
    }
}
