package com.legalfam.backend.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.legalfam.backend.chat.application.dto.ChatSendAcceptedResponse;
import com.legalfam.backend.chat.application.port.out.ChatOutboxPort;
import com.legalfam.backend.chat.application.port.out.ChatPersistencePort;
import com.legalfam.backend.chat.application.port.out.ChatUserLookupPort;
import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessing;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessingStatus;
import com.legalfam.backend.chat.domain.model.ChatMessageRole;
import com.legalfam.backend.chat.domain.model.ChatSession;
import com.legalfam.backend.payment.application.port.in.PaymentTokenUseCase;
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
    private ChatPersistencePort chatPersistencePort;

    @Mock
    private ChatOutboxPort chatOutboxPort;

    @Mock
    private ChatUserLookupPort chatUserLookupPort;

    @Mock
    private PaymentTokenUseCase paymentTokenUseCase;

    @InjectMocks
    private ChatService chatService;

    @Test
    void sendPersistsQueuedMessageConsumesTokenAndEnqueuesOutbox() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID userMessageId = UUID.randomUUID();

        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setUserId(userId);

        when(chatUserLookupPort.existsById(userId)).thenReturn(true);
        when(chatPersistencePort.findSessionById(sessionId)).thenReturn(Optional.of(session));
        when(chatPersistencePort.saveMessage(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(userMessageId);
            return message;
        });
        when(chatPersistencePort.saveMessageProcessing(any(ChatMessageProcessing.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(chatPersistencePort.saveSession(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatSendAcceptedResponse response = chatService.send(userId, "hola", sessionId);

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatPersistencePort).saveMessage(messageCaptor.capture());
        ChatMessage savedMessage = messageCaptor.getValue();
        assertEquals(ChatMessageRole.USER, savedMessage.getRole());
        assertEquals("hola", savedMessage.getContent());
        assertNotNull(savedMessage.getCreatedAt());

        ArgumentCaptor<ChatMessageProcessing> processingCaptor = ArgumentCaptor.forClass(ChatMessageProcessing.class);
        verify(chatPersistencePort).saveMessageProcessing(processingCaptor.capture());
        assertEquals(userMessageId, processingCaptor.getValue().getUserMessageId());
        assertEquals(ChatMessageProcessingStatus.QUEUED, processingCaptor.getValue().getStatus());

        verify(paymentTokenUseCase).consumeChatToken(userId, userMessageId);
        verify(chatOutboxPort).enqueueMessageQueued(sessionId, userMessageId, "hola");
        assertEquals(sessionId, response.sessionId());
        assertEquals(userMessageId, response.userMessageId());
        assertEquals("PROCESSING", response.status());
    }
}
