package com.legalfam.backend.chat.infrastructure.adapter.in;

import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import com.legalfam.backend.chat.infrastructure.config.ChatRabbitProperties;
import com.legalfam.backend.chat.infrastructure.messaging.ConfirmedRabbitSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Lleva la consulta ya guardada a la cola del broker, donde cualquier instancia la toma y, si
 * esa instancia muere, el broker se la entrega a otra. Si el broker no confirma, la consulta
 * se procesa en esta instancia: el usuario no espera a que vuelva el broker.
 */
@Service
public class ChatMessageQueuedDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageQueuedDispatcher.class);

    private final ChatQueuedMessageEventHandler chatQueuedMessageEventHandler;
    private final ConfirmedRabbitSender sender;
    private final String routingKey;

    public ChatMessageQueuedDispatcher(
            ChatQueuedMessageEventHandler chatQueuedMessageEventHandler,
            ObjectProvider<ConfirmedRabbitSender> sender,
            ChatRabbitProperties properties
    ) {
        this.chatQueuedMessageEventHandler = chatQueuedMessageEventHandler;
        this.sender = sender.getIfAvailable();
        this.routingKey = properties.chatMessageQueuedRoutingKey();
    }

    @Async("chatTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(ChatMessageQueuedEvent event) {
        if (sender != null) {
            try {
                sender.send(routingKey, event, event.userMessageId().toString());
                return;
            } catch (RuntimeException ex) {
                log.warn("Processing chat message locally, broker unavailable userMessageId={}: {}",
                        event.userMessageId(), ex.getMessage());
            }
        }
        chatQueuedMessageEventHandler.process(event, false);
    }
}
