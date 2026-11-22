package com.legalfam.backend.chat.infrastructure.adapter.in;

import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * El mensaje se confirma al broker solo cuando la consulta termino. Si la instancia muere a mitad
 * de la llamada al agente, el broker lo reentrega a otra instancia, que retoma la consulta.
 */
@Service
@ConditionalOnProperty(name = "app.chat.messaging.rabbit.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitChatMessageQueuedListener {

    private final ObjectMapper objectMapper;
    private final ChatQueuedMessageEventHandler chatQueuedMessageEventHandler;

    public RabbitChatMessageQueuedListener(
            ObjectMapper objectMapper,
            ChatQueuedMessageEventHandler chatQueuedMessageEventHandler
    ) {
        this.objectMapper = objectMapper;
        this.chatQueuedMessageEventHandler = chatQueuedMessageEventHandler;
    }

    // ponytail: una reentrega por un corte de conexion con la instancia original viva duplica la
    // llamada al agente; el cobro y la respuesta no se duplican, porque el procesamiento ya
    // terminado descarta la segunda. Si el costo pesa, guardar un lease con la instancia.
    @RabbitListener(
            queues = "${app.chat.messaging.rabbit.queue.chat-message-queued}",
            containerFactory = "chatMessageQueuedContainerFactory"
    )
    public void process(String payload, @Header(AmqpHeaders.REDELIVERED) boolean redelivered) {
        chatQueuedMessageEventHandler.process(parseEvent(payload), redelivered);
    }

    private ChatMessageQueuedEvent parseEvent(String payload) {
        try {
            return objectMapper.readValue(payload, ChatMessageQueuedEvent.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid chat message queued event payload", ex);
        }
    }
}
