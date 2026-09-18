package com.legalfam.backend.chat.infrastructure.worker;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.legalfam.backend.chat.application.port.in.IChatAssistantPersistenceUseCase;
import com.legalfam.backend.chat.application.port.out.IChatPersistencePort;
import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.infrastructure.config.N8nProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatStaleProcessingSweeperTest {

    @Mock
    private IChatPersistencePort chatPersistencePort;

    @Mock
    private IChatAssistantPersistenceUseCase chatAssistantPersistenceUseCase;

    @Test
    void failsMessagesLeftInProcessingPastTheAgentTimeout() {
        UUID sessionId = UUID.randomUUID();
        ChatMessage userMessage = ChatMessage.userMessage(sessionId, "hola", Instant.now());
        when(chatPersistencePort.findActiveMessageProcessingUpdatedBefore(any())).thenReturn(List.of(userMessage.getId()));
        when(chatPersistencePort.findMessageById(userMessage.getId())).thenReturn(Optional.of(userMessage));

        new ChatStaleProcessingSweeper(
                chatPersistencePort,
                chatAssistantPersistenceUseCase,
                new N8nProperties(null, null, null, 300_000)
        ).failStaleProcessing();

        ArgumentCaptor<Instant> threshold = ArgumentCaptor.forClass(Instant.class);
        verify(chatPersistencePort).findActiveMessageProcessingUpdatedBefore(threshold.capture());
        Duration age = Duration.between(threshold.getValue(), Instant.now());
        assertTrue(age.compareTo(Duration.ofMinutes(6)) >= 0 && age.compareTo(Duration.ofMinutes(7)) < 0, age.toString());
        verify(chatAssistantPersistenceUseCase).persistAssistantFailure(
                sessionId,
                userMessage.getId(),
                "upstream_timeout",
                "Assistant service timed out"
        );
    }
}
