package com.legalfam.backend.chat.application.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.legalfam.backend.chat.application.dto.ChatAssistantErrorDispatch;
import com.legalfam.backend.chat.application.dto.ChatAssistantGatewayResponse;
import com.legalfam.backend.chat.application.dto.ChatAssistantMetadata;
import com.legalfam.backend.chat.application.dto.ChatCitationResponse;
import com.legalfam.backend.chat.application.dto.ChatPreviousMessage;
import com.legalfam.backend.chat.application.event.ChatAssistantErrorEvent;
import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import com.legalfam.backend.chat.application.port.in.IChatAssistantPersistenceUseCase;
import com.legalfam.backend.chat.application.port.out.IChatAssistantDeliveryPort;
import com.legalfam.backend.chat.application.port.out.IChatAssistantGatewayPort;
import com.legalfam.backend.chat.domain.exception.ChatUpstreamException;
import com.legalfam.backend.chat.domain.exception.ChatApiError;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        ChatMessageQueuedEvent event = new ChatMessageQueuedEvent(sessionId, userMessageId, "hola", previousMessages);
        ChatAssistantMetadata metadata = new ChatAssistantMetadata(
                "HIGH",
                "clear question",
                List.of("review documents"),
                false,
                "GOOD",
                3
        );
        List<ChatCitationResponse> citations = List.of(new ChatCitationResponse("source", "snippet", "https://example.test"));
        ChatAssistantGatewayResponse response = new ChatAssistantGatewayResponse("respuesta", citations, metadata);

        when(IChatAssistantPersistenceUseCase.markUserMessageProcessing(userMessageId)).thenReturn(true);
        when(IChatAssistantGatewayPort.sendMessage("hola", sessionId, previousMessages)).thenReturn(response);

        chatQueuedMessageService.process(event);

        verify(IChatAssistantGatewayPort).sendMessage("hola", sessionId, previousMessages);
        verify(IChatAssistantPersistenceUseCase).persistAssistantMessage(
                sessionId,
                userMessageId,
                "respuesta",
                citations,
                metadata
        );
        verify(IChatAssistantDeliveryPort, never()).dispatchAssistantError(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void processPersistsAndDispatchesFailureWhenAssistantGatewayFails() {
        UUID sessionId = UUID.randomUUID();
        UUID userMessageId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID failureMessageId = UUID.randomUUID();
        ChatMessageQueuedEvent event = new ChatMessageQueuedEvent(sessionId, userMessageId, "hola", List.of());
        ChatAssistantErrorEvent errorEvent = new ChatAssistantErrorEvent(
                sessionId,
                failureMessageId,
                "upstream_timeout",
                "Assistant service timed out",
                Instant.now()
        );
        ChatAssistantErrorDispatch dispatch = new ChatAssistantErrorDispatch(userId, sessionId, errorEvent);

        when(IChatAssistantPersistenceUseCase.markUserMessageProcessing(userMessageId)).thenReturn(true);
        when(IChatAssistantGatewayPort.sendMessage("hola", sessionId, List.of()))
                .thenThrow(ChatUpstreamException.of(ChatApiError.UPSTREAM_TIMEOUT));
        when(IChatAssistantPersistenceUseCase.persistAssistantFailure(
                sessionId,
                userMessageId,
                "upstream_timeout",
                "Assistant service timed out"
        )).thenReturn(dispatch);

        chatQueuedMessageService.process(event);

        verify(IChatAssistantPersistenceUseCase).persistAssistantFailure(
                sessionId,
                userMessageId,
                "upstream_timeout",
                "Assistant service timed out"
        );
        verify(IChatAssistantDeliveryPort).dispatchAssistantError(userId, sessionId, errorEvent);
    }
}
