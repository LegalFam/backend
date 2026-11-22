package com.legalfam.backend.chat.infrastructure.adapter.out;

import com.legalfam.backend.chat.application.event.ChatAssistantDeliveryQueuedEvent;
import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import com.legalfam.backend.chat.application.port.out.IChatEventPublisherPort;
import com.legalfam.backend.chat.infrastructure.config.ChatRabbitProperties;
import com.legalfam.backend.chat.infrastructure.messaging.ConfirmedRabbitSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.chat.messaging.rabbit.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitChatEventPublisherAdapter implements IChatEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(RabbitChatEventPublisherAdapter.class);

    private final ConfirmedRabbitSender sender;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final String assistantDeliveryRoutingKey;

    public RabbitChatEventPublisherAdapter(
            ConfirmedRabbitSender sender,
            ApplicationEventPublisher applicationEventPublisher,
            ChatRabbitProperties properties
    ) {
        this.sender = sender;
        this.applicationEventPublisher = applicationEventPublisher;
        this.assistantDeliveryRoutingKey = properties.assistantDeliveryRoutingKey();
    }

    /**
     * Se publica dentro de la transaccion del envio; ChatMessageQueuedDispatcher lo pasa al broker
     * despues del commit, para no encolar una consulta que termino en rollback.
     */
    @Override
    public void publishMessageQueued(ChatMessageQueuedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    @Override
    public void publishAssistantDelivery(ChatAssistantDeliveryQueuedEvent event) {
        sender.send(assistantDeliveryRoutingKey, event, event.assistantMessageId().toString());
        log.debug("Published assistant delivery event to RabbitMQ: sessionId={}, messageId={}",
                event.chatSessionId(), event.assistantMessageId());
    }
}
