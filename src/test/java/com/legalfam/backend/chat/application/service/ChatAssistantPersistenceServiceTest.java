package com.legalfam.backend.chat.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.legalfam.backend.chat.application.dto.ChatAssistantMetadata;
import com.legalfam.backend.chat.application.port.out.IChatOutboxPort;
import com.legalfam.backend.chat.application.port.out.IChatPersistencePort;
import com.legalfam.backend.chat.application.port.out.IChatTokenPort;
import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessing;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessingStatus;
import com.legalfam.backend.chat.domain.model.ChatSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatAssistantPersistenceServiceTest {

    @Mock
    private IChatPersistencePort chatPersistencePort;

    @Mock
    private IChatOutboxPort chatOutboxPort;

    @Mock
    private IChatTokenPort chatTokenPort;

    @InjectMocks
    private ChatAssistantPersistenceService chatAssistantPersistenceService;

    @Test
    void persistAssistantMessageConsumesRagTokensForAssistantResult() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID userMessageId = UUID.randomUUID();
        ChatSession session = ChatSession.restore(sessionId, userId, null, Instant.now(), Instant.now());
        ChatMessage userMessage = ChatMessage.userMessage(sessionId, "hola", Instant.now());
        ChatAssistantMetadata metadata = new ChatAssistantMetadata(
                "MEDIUM",
                null,
                List.of(),
                false,
                "GOOD",
                3
        );

        when(chatPersistencePort.findSessionById(sessionId)).thenReturn(Optional.of(session));
        when(chatPersistencePort.saveMessage(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatPersistencePort.findMessageById(userMessageId)).thenReturn(Optional.of(userMessage));
        when(chatPersistencePort.findMessageProcessingByUserMessageIdForUpdate(userMessageId))
                .thenReturn(Optional.of(processing(userMessageId)));
        when(chatPersistencePort.saveMessageProcessing(any(ChatMessageProcessing.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(chatPersistencePort.saveSession(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        chatAssistantPersistenceService.persistAssistantMessage(
                sessionId,
                userMessageId,
                "respuesta",
                List.of(),
                metadata
        );

        verify(chatTokenPort).consumeChatTokensForAssistantResult(userId, userMessageId, 3);
    }

    private ChatMessageProcessing processing(UUID userMessageId) {
        Instant now = Instant.now();
        return ChatMessageProcessing.restore(
                UUID.randomUUID(),
                userMessageId,
                ChatMessageProcessingStatus.PROCESSING,
                null,
                null,
                now,
                null,
                now,
                now
        );
    }
}
