package com.legalfam.backend.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import com.legalfam.backend.chat.application.dto.ChatSendAcceptedResponse;
import com.legalfam.backend.chat.application.mapper.ChatMessageResponseMapper;
import com.legalfam.backend.chat.application.policy.ChatAccessPolicy;
import com.legalfam.backend.chat.application.policy.ChatPrivacyPolicy;
import com.legalfam.backend.chat.application.port.out.IChatEventPublisherPort;
import com.legalfam.backend.chat.application.port.out.IChatPersistencePort;
import com.legalfam.backend.chat.application.port.out.IChatTokenPort;
import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessing;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessingStatus;
import com.legalfam.backend.chat.domain.model.ChatMessageRole;
import com.legalfam.backend.chat.domain.model.ChatSession;
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
    private IChatTokenPort IChatTokenPort;

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
    void sendPersistsQueuedMessageConsumesTokenAndPublishesLocalAsyncEvent() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setUserId(userId);

        when(chatAccessPolicy.requireSessionOwner(userId, sessionId)).thenReturn(session);
        when(IChatPersistencePort.existsUnreadAssistantMessageBySessionId(sessionId)).thenReturn(false);
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

        verify(IChatTokenPort).consumeChatToken(userId, response.userMessageId());
        verify(IChatEventPublisherPort).publishMessageQueued(new ChatMessageQueuedEvent(sessionId, response.userMessageId(), "hola"));
        assertEquals(sessionId, response.sessionId());
        assertEquals(savedMessage.getId(), response.userMessageId());
        assertEquals("PROCESSING", response.status());
    }
}
