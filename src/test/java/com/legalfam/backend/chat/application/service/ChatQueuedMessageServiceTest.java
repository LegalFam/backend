package com.legalfam.backend.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.legalfam.backend.chat.application.dto.ChatAssistantGatewayResponse;
import com.legalfam.backend.chat.application.dto.ChatAssistantMetadata;
import com.legalfam.backend.chat.application.dto.ChatCitationResponse;
import com.legalfam.backend.chat.application.dto.ChatPreviousMessage;
import com.legalfam.backend.chat.application.event.ChatAssistantDraftEvent;
import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import com.legalfam.backend.chat.application.port.in.IChatAssistantPersistenceUseCase;
import com.legalfam.backend.chat.application.port.out.IChatAssistantDeliveryPort;
import com.legalfam.backend.chat.application.port.out.IChatAssistantDraftListener;
import com.legalfam.backend.chat.application.port.out.IChatAssistantGatewayPort;
import com.legalfam.backend.chat.domain.exception.ChatUpstreamException;
import com.legalfam.backend.chat.domain.exception.ChatApiError;
import com.legalfam.backend.chat.domain.model.ChatLanguage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatQueuedMessageServiceTest {

    @Mock
    private IChatAssistantGatewayPort IChatAssistantGatewayPort;

    @Mock
    private IChatAssistantPersistenceUseCase IChatAssistantPersistenceUseCase;

    @Mock
    private IChatAssistantDeliveryPort IChatAssistantDeliveryPort;

    @InjectMocks
    private ChatQueuedMessageService chatQueuedMessageService;

    @Test
    void processPersistsAssistantMessageThroughApplicationPorts() {
        UUID sessionId = UUID.randomUUID();
        UUID userMessageId = UUID.randomUUID();
        List<ChatPreviousMessage> previousMessages = List.of(new ChatPreviousMessage(
                "USER",
                "antes",
                Instant.parse("2026-01-01T00:00:00Z")
        ));
        ChatMessageQueuedEvent event = new ChatMessageQueuedEvent(sessionId, userMessageId, "hola", previousMessages, "es");
        ChatAssistantMetadata metadata = new ChatAssistantMetadata(
                "HIGH",
                "clear question",
                List.of("review documents"),
                List.of(),
                false,
                "GOOD",
                3
        );
        List<ChatCitationResponse> citations = List.of(new ChatCitationResponse("source", "snippet", null, "pasaje literal", "https://example.test", null, null, null));
        ChatAssistantGatewayResponse response = new ChatAssistantGatewayResponse("respuesta", null, null, null, citations, metadata);

        when(IChatAssistantPersistenceUseCase.markUserMessageProcessing(userMessageId)).thenReturn(true);
        when(IChatAssistantGatewayPort.sendMessage(eq("hola"), eq(sessionId), eq(previousMessages), eq(ChatLanguage.ES), any()))
                .thenReturn(response);

        chatQueuedMessageService.process(event);

        verify(IChatAssistantGatewayPort).sendMessage(eq("hola"), eq(sessionId), eq(previousMessages), eq(ChatLanguage.ES), any());
        verify(IChatAssistantPersistenceUseCase).persistAssistantMessage(
                sessionId,
                userMessageId,
                response,
                ChatLanguage.ES
        );
        verify(IChatAssistantPersistenceUseCase, never()).persistAssistantFailure(any(), any(), any(), any());
    }

    @Test
    void processPersistsFailureWhenAssistantGatewayFails() {
        UUID sessionId = UUID.randomUUID();
        UUID userMessageId = UUID.randomUUID();
        ChatMessageQueuedEvent event = new ChatMessageQueuedEvent(sessionId, userMessageId, "hola", List.of(), "es");

        when(IChatAssistantPersistenceUseCase.markUserMessageProcessing(userMessageId)).thenReturn(true);
        when(IChatAssistantGatewayPort.sendMessage(eq("hola"), eq(sessionId), eq(List.of()), eq(ChatLanguage.ES), any()))
                .thenThrow(ChatUpstreamException.of(ChatApiError.UPSTREAM_TIMEOUT));

        chatQueuedMessageService.process(event);

        verify(IChatAssistantPersistenceUseCase).persistAssistantFailure(
                sessionId,
                userMessageId,
                "upstream_timeout",
                "Assistant service timed out"
        );
    }

    @Test
    void spanishTurnsForwardDraftsToTheChat() {
        UUID sessionId = UUID.randomUUID();
        UUID userMessageId = UUID.randomUUID();
        ChatMessageQueuedEvent event = new ChatMessageQueuedEvent(sessionId, userMessageId, "hola", List.of(), "es");
        when(IChatAssistantPersistenceUseCase.markUserMessageProcessing(userMessageId)).thenReturn(true);
        when(IChatAssistantGatewayPort.sendMessage(eq("hola"), eq(sessionId), eq(List.of()), eq(ChatLanguage.ES), any()))
                .thenAnswer(invocation -> {
                    IChatAssistantDraftListener listener = invocation.getArgument(4);
                    listener.onDraft("Con lo que");
                    // Llega antes del intervalo minimo: se omite.
                    listener.onDraft("Con lo que cuentas");
                    // Un reintento del flujo se manda siempre.
                    listener.onDraft("");
                    return simpleResponse();
                });

        chatQueuedMessageService.process(event);

        ArgumentCaptor<ChatAssistantDraftEvent> drafts = ArgumentCaptor.forClass(ChatAssistantDraftEvent.class);
        verify(IChatAssistantDeliveryPort, times(2)).dispatchAssistantDraft(drafts.capture());
        ChatAssistantDraftEvent first = drafts.getAllValues().getFirst();
        assertEquals(sessionId, first.sessionId());
        assertEquals(userMessageId, first.userMessageId());
        assertEquals("Con lo que", first.text());
        assertEquals(1, first.sequence());
        assertEquals("", drafts.getAllValues().get(1).text());
        assertEquals(2, drafts.getAllValues().get(1).sequence());
    }

    @Test
    void translatedTurnsDoNotStreamTheSpanishDraft() {
        UUID sessionId = UUID.randomUUID();
        UUID userMessageId = UUID.randomUUID();
        ChatMessageQueuedEvent event = new ChatMessageQueuedEvent(sessionId, userMessageId, "napaykullayki", List.of(), "qu");
        when(IChatAssistantPersistenceUseCase.markUserMessageProcessing(userMessageId)).thenReturn(true);
        ArgumentCaptor<IChatAssistantDraftListener> listener = ArgumentCaptor.forClass(IChatAssistantDraftListener.class);
        when(IChatAssistantGatewayPort.sendMessage(eq("napaykullayki"), eq(sessionId), eq(List.of()), eq(ChatLanguage.QU), listener.capture()))
                .thenReturn(simpleResponse());

        chatQueuedMessageService.process(event);

        assertSame(IChatAssistantDraftListener.NONE, listener.getValue());
        verify(IChatAssistantDeliveryPort, never()).dispatchAssistantDraft(any());
    }

    private ChatAssistantGatewayResponse simpleResponse() {
        ChatAssistantMetadata metadata = new ChatAssistantMetadata(null, null, List.of(), List.of(), false, "NONE", 1);
        return new ChatAssistantGatewayResponse("respuesta", null, null, null, List.of(), metadata);
    }
}
