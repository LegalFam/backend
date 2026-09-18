package com.legalfam.backend.chat.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.legalfam.backend.chat.application.dto.ChatAssistantGatewayResponse;
import com.legalfam.backend.chat.application.dto.ChatAssistantMetadata;
import com.legalfam.backend.chat.application.dto.ChatCitationResponse;
import com.legalfam.backend.chat.application.dto.ChatPreviousMessage;
import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import com.legalfam.backend.chat.application.port.in.IChatAssistantPersistenceUseCase;
import com.legalfam.backend.chat.application.port.out.IChatAssistantGatewayPort;
import com.legalfam.backend.chat.domain.exception.ChatUpstreamException;
import com.legalfam.backend.chat.domain.exception.ChatApiError;
import com.legalfam.backend.chat.domain.model.ChatLanguage;
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
        when(IChatAssistantGatewayPort.sendMessage("hola", sessionId, previousMessages, ChatLanguage.ES))
                .thenReturn(response);

        chatQueuedMessageService.process(event);

        verify(IChatAssistantGatewayPort).sendMessage("hola", sessionId, previousMessages, ChatLanguage.ES);
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
        when(IChatAssistantGatewayPort.sendMessage("hola", sessionId, List.of(), ChatLanguage.ES))
                .thenThrow(ChatUpstreamException.of(ChatApiError.UPSTREAM_TIMEOUT));

        chatQueuedMessageService.process(event);

        verify(IChatAssistantPersistenceUseCase).persistAssistantFailure(
                sessionId,
                userMessageId,
                "upstream_timeout",
                "Assistant service timed out"
        );
    }
}
