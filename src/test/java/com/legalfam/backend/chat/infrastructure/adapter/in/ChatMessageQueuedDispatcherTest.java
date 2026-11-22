package com.legalfam.backend.chat.infrastructure.adapter.in;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import com.legalfam.backend.chat.infrastructure.config.ChatRabbitProperties;
import com.legalfam.backend.chat.infrastructure.messaging.ConfirmedRabbitSender;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ChatMessageQueuedDispatcherTest {

    private static final String ROUTING_KEY = "chat.message.queued.v1";

    private final ChatQueuedMessageEventHandler handler = mock(ChatQueuedMessageEventHandler.class);
    private final ConfirmedRabbitSender sender = mock(ConfirmedRabbitSender.class);
    private final ChatMessageQueuedEvent event =
            new ChatMessageQueuedEvent(UUID.randomUUID(), UUID.randomUUID(), "hola", List.of(), "es");

    @Test
    void sendsTheQueryToTheBrokerInsteadOfProcessingItHere() {
        dispatcher(sender).dispatch(event);

        verify(sender).send(ROUTING_KEY, event, event.userMessageId().toString());
        verify(handler, never()).process(any(), eq(false));
    }

    @Test
    void processesLocallyWhenTheConfirmedQueryIsNotTakenInTime() {
        dispatcher(sender, 0L).dispatch(event);

        verify(handler, timeout(1000)).process(event, false);
    }

    @Test
    void processesLocallyWhenTheBrokerDoesNotConfirm() {
        doThrow(new IllegalStateException("broker down")).when(sender).send(any(), any(), any());

        dispatcher(sender).dispatch(event);

        verify(handler).process(event, false);
    }

    @Test
    void processesLocallyWhenRabbitIsDisabled() {
        dispatcher(null).dispatch(event);

        verify(handler).process(event, false);
    }

    @SuppressWarnings("unchecked")
    private ChatMessageQueuedDispatcher dispatcher(ConfirmedRabbitSender available) {
        return dispatcher(available, 60_000L);
    }

    @SuppressWarnings("unchecked")
    private ChatMessageQueuedDispatcher dispatcher(ConfirmedRabbitSender available, long localFallbackMs) {
        ObjectProvider<ConfirmedRabbitSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(available);
        ChatRabbitProperties properties = new ChatRabbitProperties(
                "chat.events.x", "chat.events.dlx", "chat.assistant.delivery.q", "chat.assistant.delivery.dlq",
                "chat.assistant.delivery.v1", "chat.assistant.delivery.dlq", "chat.message.queued.q", ROUTING_KEY,
                "chat.message.queued.dlq", "chat.message.queued.dlq", 2, 10800000L, 5000L
        );
        return new ChatMessageQueuedDispatcher(handler, provider, properties, Runnable::run, localFallbackMs);
    }
}
