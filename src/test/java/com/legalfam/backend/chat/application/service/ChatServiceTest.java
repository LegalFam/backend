package com.legalfam.backend.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.legalfam.backend.chat.application.dto.ChatPreviousMessage;
import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import com.legalfam.backend.chat.application.dto.ChatSendAcceptedResponse;
import com.legalfam.backend.chat.application.service.mapper.ChatMessageResponseMapper;
import com.legalfam.backend.chat.application.policy.ChatAccessPolicy;
import com.legalfam.backend.chat.application.policy.ChatPrivacyPolicy;
import com.legalfam.backend.chat.application.port.out.IChatEventPublisherPort;
import com.legalfam.backend.chat.application.port.out.IChatPersistencePort;
import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessing;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessingStatus;
import com.legalfam.backend.chat.domain.model.ChatMessageRole;
import com.legalfam.backend.chat.domain.model.ChatSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private IChatPersistencePort IChatPersistencePort;

    @Mock
    private IChatEventPublisherPort IChatEventPublisherPort;

    @Mock
    private ChatAccessPolicy chatAccessPolicy;

    @Mock
    private ChatPrivacyPolicy chatPrivacyPolicy;

    @Mock
    private ChatMessageResponseMapper chatMessageResponseMapper;

    @InjectMocks
    private ChatService chatService;

    @Test
    void sendPersistsQueuedMessageWithoutConsumingTokenAndPublishesLocalAsyncEvent() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        ChatSession session = ChatSession.restore(sessionId, userId, null, Instant.now(), Instant.now());

        when(chatAccessPolicy.requireSessionOwner(userId, sessionId)).thenReturn(session);
        when(IChatPersistencePort.findActiveMessageProcessingByUserId(userId)).thenReturn(Optional.empty());
        when(IChatPersistencePort.existsUnreadAssistantMessageBySessionId(sessionId)).thenReturn(false);
        when(IChatPersistencePort.findRecentMessagesForAssistantContext(sessionId, 12)).thenReturn(List.of(
                ChatMessage.userMessage(sessionId, "antes", Instant.parse("2026-01-01T00:00:00Z")),
                ChatMessage.assistantMessage(sessionId, "respuesta anterior", Instant.parse("2026-01-01T00:01:00Z"))
        ));
        when(IChatPersistencePort.saveMessage(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(IChatPersistencePort.saveMessageProcessing(any(ChatMessageProcessing.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(IChatPersistencePort.saveSession(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatSendAcceptedResponse response = chatService.send(userId, "hola", sessionId);

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(IChatPersistencePort).saveMessage(messageCaptor.capture());
        ChatMessage savedMessage = messageCaptor.getValue();
        assertEquals(ChatMessageRole.USER, savedMessage.getRole());
        assertEquals("hola", savedMessage.getContent());
        assertNotNull(savedMessage.getCreatedAt());

        ArgumentCaptor<ChatMessageProcessing> processingCaptor = ArgumentCaptor.forClass(ChatMessageProcessing.class);
        verify(IChatPersistencePort).saveMessageProcessing(processingCaptor.capture());
        assertEquals(response.userMessageId(), processingCaptor.getValue().getUserMessageId());
        assertEquals(ChatMessageProcessingStatus.QUEUED, processingCaptor.getValue().getStatus());

        ArgumentCaptor<ChatMessageQueuedEvent> eventCaptor = ArgumentCaptor.forClass(ChatMessageQueuedEvent.class);
        verify(IChatEventPublisherPort).publishMessageQueued(eventCaptor.capture());
        ChatMessageQueuedEvent event = eventCaptor.getValue();
        assertEquals(sessionId, event.chatSessionId());
        assertEquals(response.userMessageId(), event.userMessageId());
        assertEquals("hola", event.userMessageInput());
        assertEquals(List.of(
                new ChatPreviousMessage("USER", "antes", Instant.parse("2026-01-01T00:00:00Z")),
                new ChatPreviousMessage("ASSISTANT", "respuesta anterior", Instant.parse("2026-01-01T00:01:00Z"))
        ), event.previousMessages());
        assertEquals(sessionId, response.sessionId());
        assertEquals(savedMessage.getId(), response.userMessageId());
        assertEquals("PROCESSING", response.status());
    }
}
