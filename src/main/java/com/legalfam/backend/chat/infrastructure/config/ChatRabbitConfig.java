package com.legalfam.backend.chat.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
@ConditionalOnProperty(name = "app.chat.messaging.rabbit.enabled", havingValue = "true", matchIfMissing = true)
public class ChatRabbitConfig {

    @Bean
    public TopicExchange chatEventsExchange(ChatRabbitProperties properties) {
        return new TopicExchange(properties.exchangeName(), true, false);
    }

    @Bean
    public DirectExchange chatDeadLetterExchange(ChatRabbitProperties properties) {
        return new DirectExchange(properties.deadLetterExchangeName(), true, false);
    }

    @Bean
    public Queue assistantDeliveryQueue(ChatRabbitProperties properties) {
        return QueueBuilder.durable(properties.assistantDeliveryQueueName())
                .deadLetterExchange(properties.deadLetterExchangeName())
                .deadLetterRoutingKey(properties.deadLetterRoutingKey())
                .ttl((int) properties.safeQueueTtlMs())
                .withArgument("x-queue-type", "quorum")
                .build();
    }

    @Bean
    public Queue assistantDeliveryDeadLetterQueue(ChatRabbitProperties properties) {
        return QueueBuilder.durable(properties.deadLetterQueueName()).build();
    }

    @Bean
    public Binding assistantDeliveryBinding(
            @Qualifier("assistantDeliveryQueue") Queue assistantDeliveryQueue,
            TopicExchange chatEventsExchange,
            ChatRabbitProperties properties
    ) {
        return BindingBuilder.bind(assistantDeliveryQueue).to(chatEventsExchange).with(properties.assistantDeliveryRoutingKey());
    }

    @Bean
    public Binding assistantDeliveryDeadLetterBinding(
            @Qualifier("assistantDeliveryDeadLetterQueue") Queue assistantDeliveryDeadLetterQueue,
            DirectExchange chatDeadLetterExchange,
            ChatRabbitProperties properties
    ) {
        return BindingBuilder.bind(assistantDeliveryDeadLetterQueue)
                .to(chatDeadLetterExchange)
                .with(properties.deadLetterRoutingKey());
    }
}
