package com.legalfam.backend.chat.infrastructure.adapter.in;

import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import com.legalfam.backend.chat.infrastructure.config.ChatRabbitProperties;
import com.legalfam.backend.chat.infrastructure.messaging.ConfirmedRabbitSender;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Lleva la consulta ya guardada a la cola del broker, donde cualquier instancia la toma y, si
 * esa instancia muere, el broker se la entrega a otra. Si el broker no confirma, la consulta
 * se procesa en esta instancia: el usuario no espera a que vuelva el broker. Si confirma pero
 * nadie la toma a tiempo (el broker cayó con la consulta dentro), también se procesa aquí; si
 * el broker la entrega después, ya está en proceso y se descarta.
 */
@Service
public class ChatMessageQueuedDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageQueuedDispatcher.class);

    private final ChatQueuedMessageEventHandler chatQueuedMessageEventHandler;
    private final ConfirmedRabbitSender sender;
    private final String routingKey;
    private final Executor localFallback;

    public ChatMessageQueuedDispatcher(
            ChatQueuedMessageEventHandler chatQueuedMessageEventHandler,
            ObjectProvider<ConfirmedRabbitSender> sender,
            ChatRabbitProperties properties,
            @Qualifier("chatTaskExecutor") Executor chatTaskExecutor,
            @Value("${app.chat.messaging.rabbit.chat-message-queued-local-fallback-ms:120000}") long localFallbackMs
    ) {
        this.chatQueuedMessageEventHandler = chatQueuedMessageEventHandler;
        this.sender = sender.getIfAvailable();
        this.routingKey = properties.chatMessageQueuedRoutingKey();
        this.localFallback = CompletableFuture.delayedExecutor(localFallbackMs, TimeUnit.MILLISECONDS, chatTaskExecutor);
    }

    @Async("chatTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(ChatMessageQueuedEvent event) {
        if (sender != null) {
            try {
                sender.send(routingKey, event, event.userMessageId().toString());
                // ponytail: el respaldo vive en memoria; si esta instancia muere antes, queda el barrido de atascadas.
                localFallback.execute(() -> chatQueuedMessageEventHandler.process(event, false));
                return;
            } catch (RuntimeException ex) {
                log.warn("Processing chat message locally, broker unavailable userMessageId={}: {}",
                        event.userMessageId(), ex.getMessage());
            }
        }
        chatQueuedMessageEventHandler.process(event, false);
    }
}
