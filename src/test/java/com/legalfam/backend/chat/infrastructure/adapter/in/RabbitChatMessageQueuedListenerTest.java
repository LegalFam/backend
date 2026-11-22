package com.legalfam.backend.chat.infrastructure.adapter.in;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RabbitChatMessageQueuedListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatQueuedMessageEventHandler handler = mock(ChatQueuedMessageEventHandler.class);
    private final RabbitChatMessageQueuedListener listener = new RabbitChatMessageQueuedListener(objectMapper, handler);
    private final ChatMessageQueuedEvent event =
            new ChatMessageQueuedEvent(UUID.randomUUID(), UUID.randomUUID(), "hola", List.of(), "es");

    @Test
    void firstDeliveryDoesNotTakeOverAMessageInProcessing() {
        listener.process(objectMapper.writeValueAsString(event), false);

        verify(handler).process(event, false);
    }

    @Test
    void redeliveryAfterAConsumerDiedTakesTheMessageOver() {
        listener.process(objectMapper.writeValueAsString(event), true);

        verify(handler).process(event, true);
    }

    @Test
    void rejectsAnUnreadablePayloadSoTheBrokerDeadLettersIt() {
        assertThrows(IllegalArgumentException.class, () -> listener.process("{not json", false));
    }
}
