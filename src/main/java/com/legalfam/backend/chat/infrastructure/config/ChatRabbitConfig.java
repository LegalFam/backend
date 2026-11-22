package com.legalfam.backend.chat.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * Quorum para que la reentrega tras la caida de un consumidor cuente intentos: pasado el
     * limite, la consulta va a la cola de fallidos y el sweeper la cierra con error.
     */
    @Bean
    public Queue chatMessageQueuedQueue(ChatRabbitProperties properties) {
        return QueueBuilder.durable(properties.chatMessageQueuedQueueName())
                .deadLetterExchange(properties.deadLetterExchangeName())
                .deadLetterRoutingKey(properties.chatMessageQueuedDeadLetterRoutingKey())
                .ttl((int) properties.safeQueueTtlMs())
                .withArgument("x-queue-type", "quorum")
                .withArgument("x-delivery-limit", properties.safeChatMessageQueuedDeliveryLimit())
                .build();
    }

    @Bean
    public Queue chatMessageQueuedDeadLetterQueue(ChatRabbitProperties properties) {
        return QueueBuilder.durable(properties.chatMessageQueuedDeadLetterQueueName()).build();
    }

    @Bean
    public Binding chatMessageQueuedBinding(
            @Qualifier("chatMessageQueuedQueue") Queue chatMessageQueuedQueue,
            TopicExchange chatEventsExchange,
            ChatRabbitProperties properties
    ) {
        return BindingBuilder.bind(chatMessageQueuedQueue).to(chatEventsExchange).with(properties.chatMessageQueuedRoutingKey());
    }

    @Bean
    public Binding chatMessageQueuedDeadLetterBinding(
            @Qualifier("chatMessageQueuedDeadLetterQueue") Queue chatMessageQueuedDeadLetterQueue,
            DirectExchange chatDeadLetterExchange,
            ChatRabbitProperties properties
    ) {
        return BindingBuilder.bind(chatMessageQueuedDeadLetterQueue)
                .to(chatDeadLetterExchange)
                .with(properties.chatMessageQueuedDeadLetterRoutingKey());
    }

    /** Prefetch 1: una consulta tarda decenas de segundos y no debe quedar retenida en una instancia ocupada. */
    @Bean
    public SimpleRabbitListenerContainerFactory chatMessageQueuedContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            @Value("${app.chat.messaging.rabbit.listener.chat-message-queued-concurrency:8}") int concurrency
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setPrefetchCount(1);
        factory.setConcurrentConsumers(concurrency);
        factory.setMaxConcurrentConsumers(concurrency);
        return factory;
    }
}
