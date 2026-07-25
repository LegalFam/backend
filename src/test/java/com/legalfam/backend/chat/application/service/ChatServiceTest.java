package com.legalfam.backend.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.legalfam.backend.chat.application.dto.ChatPreviousMessage;
import com.legalfam.backend.chat.application.dto.ChatRateMessageRequest;
import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import com.legalfam.backend.chat.application.dto.ChatSendAcceptedResponse;
import com.legalfam.backend.chat.application.service.mapper.ChatMessageResponseMapper;
import com.legalfam.backend.chat.application.policy.ChatAccessPolicy;
import com.legalfam.backend.chat.application.policy.ChatPrivacyPolicy;
import com.legalfam.backend.chat.application.dto.ChatEntitlements;
import com.legalfam.backend.chat.application.port.out.IChatEntitlementsPort;
import com.legalfam.backend.chat.application.port.out.IChatEventPublisherPort;
import com.legalfam.backend.chat.application.port.out.IChatPersistencePort;
import com.legalfam.backend.chat.application.port.out.IChatTokenPort;
import com.legalfam.backend.chat.domain.exception.InsufficientChatTokensException;
import com.legalfam.backend.chat.domain.exception.InvalidChatRequestException;
import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessing;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessingStatus;
import com.legalfam.backend.chat.domain.model.ChatMessageRole;
import com.legalfam.backend.chat.domain.model.ChatSession;
import com.legalfam.backend.common.cursor.CursorQuery;
import com.legalfam.backend.common.cursor.CursorResult;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    private IChatTokenPort IChatTokenPort;

    @Mock
    private IChatEntitlementsPort IChatEntitlementsPort;

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
        when(IChatTokenPort.hasChatTokensAvailable(userId)).thenReturn(true);
        when(IChatEntitlementsPort.resolveEntitlements(userId)).thenReturn(new ChatEntitlements(15, null));
        when(IChatPersistencePort.findRecentMessagesForAssistantContext(sessionId, 15)).thenReturn(List.of(
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

    @Test
    void sendRejectsWhenUserHasNoTokensWithoutPersistingOrPublishing() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ChatSession session = ChatSession.restore(sessionId, userId, null, Instant.now(), Instant.now());

        when(chatAccessPolicy.requireSessionOwner(userId, sessionId)).thenReturn(session);
        when(IChatPersistencePort.findActiveMessageProcessingByUserId(userId)).thenReturn(Optional.empty());
        when(IChatPersistencePort.existsUnreadAssistantMessageBySessionId(sessionId)).thenReturn(false);
        when(IChatTokenPort.hasChatTokensAvailable(userId)).thenReturn(false);

        InsufficientChatTokensException exception = assertThrows(
                InsufficientChatTokensException.class,
                () -> chatService.send(userId, "hola", sessionId)
        );

        assertEquals("insufficient_tokens", exception.error().code());
        verify(IChatPersistencePort, never()).saveMessage(any());
        verify(IChatPersistencePort, never()).saveMessageProcessing(any());
        verifyNoInteractions(IChatEventPublisherPort);
    }

    @Test
    void sendRejectsMissingSessionIdBeforePolicyOrPersistenceWork() {
        UUID userId = UUID.randomUUID();

        InvalidChatRequestException exception = assertThrows(
                InvalidChatRequestException.class,
                () -> chatService.send(userId, "hola", null)
        );

        assertEquals("session_id_required", exception.error().code());
        verifyNoInteractions(
                IChatPersistencePort,
                IChatEventPublisherPort,
                IChatTokenPort,
                IChatEntitlementsPort,
                chatAccessPolicy,
                chatPrivacyPolicy,
                chatMessageResponseMapper
        );
    }

    @Test
    void sendClipsPreviousMessagesBeforePublishingAssistantContext() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String longContent = "a".repeat(2_050);
        ChatSession session = ChatSession.restore(sessionId, userId, null, Instant.now(), Instant.now());

        when(chatAccessPolicy.requireSessionOwner(userId, sessionId)).thenReturn(session);
        when(IChatPersistencePort.findActiveMessageProcessingByUserId(userId)).thenReturn(Optional.empty());
        when(IChatPersistencePort.existsUnreadAssistantMessageBySessionId(sessionId)).thenReturn(false);
        when(IChatTokenPort.hasChatTokensAvailable(userId)).thenReturn(true);
        when(IChatEntitlementsPort.resolveEntitlements(userId)).thenReturn(new ChatEntitlements(15, null));
        when(IChatPersistencePort.findRecentMessagesForAssistantContext(sessionId, 15)).thenReturn(List.of(
                ChatMessage.userMessage(sessionId, "  " + longContent + "  ", Instant.parse("2026-01-01T00:00:00Z"))
        ));
        when(IChatPersistencePort.saveMessage(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(IChatPersistencePort.saveMessageProcessing(any(ChatMessageProcessing.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(IChatPersistencePort.saveSession(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        chatService.send(userId, "hola", sessionId);

        ArgumentCaptor<ChatMessageQueuedEvent> eventCaptor = ArgumentCaptor.forClass(ChatMessageQueuedEvent.class);
        verify(IChatEventPublisherPort).publishMessageQueued(eventCaptor.capture());
        String clippedContent = eventCaptor.getValue().previousMessages().getFirst().content();
        assertEquals(2_003, clippedContent.length());
        assertEquals("...", clippedContent.substring(2_000));
    }

    @Test
    void sendUsesContextMessageLimitFromUserPlanInsteadOfAFixedConstant() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ChatSession session = ChatSession.restore(sessionId, userId, null, Instant.now(), Instant.now());

        when(chatAccessPolicy.requireSessionOwner(userId, sessionId)).thenReturn(session);
        when(IChatPersistencePort.findActiveMessageProcessingByUserId(userId)).thenReturn(Optional.empty());
        when(IChatPersistencePort.existsUnreadAssistantMessageBySessionId(sessionId)).thenReturn(false);
        when(IChatTokenPort.hasChatTokensAvailable(userId)).thenReturn(true);
        when(IChatEntitlementsPort.resolveEntitlements(userId)).thenReturn(new ChatEntitlements(25, null));
        when(IChatPersistencePort.findRecentMessagesForAssistantContext(sessionId, 25)).thenReturn(List.of());
        when(IChatPersistencePort.saveMessage(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));
        when(IChatPersistencePort.saveMessageProcessing(any(ChatMessageProcessing.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(IChatPersistencePort.saveSession(any(ChatSession.class))).thenAnswer(i -> i.getArgument(0));

        chatService.send(userId, "hola", sessionId);

        verify(IChatPersistencePort).findRecentMessagesForAssistantContext(sessionId, 25);
        verify(IChatPersistencePort, never()).findRecentMessagesForAssistantContext(eq(sessionId), intThat(l -> l != 25));
    }

    @Test
    void listSessionsRestrictsToHistoryWindowWhenPlanDefinesOne() {
        UUID userId = UUID.randomUUID();
        CursorQuery cursorQuery = new CursorQuery(0, 20);
        when(IChatEntitlementsPort.resolveEntitlements(userId)).thenReturn(new ChatEntitlements(10, 30));
        when(IChatPersistencePort.findSessionsByUserIdUpdatedAfterOrderByUpdatedAtDesc(
                eq(userId), any(Instant.class), eq(cursorQuery)
        )).thenReturn(new CursorResult<>(List.of(), null));

        chatService.listSessions(userId, cursorQuery);

        ArgumentCaptor<Instant> thresholdCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(IChatPersistencePort).findSessionsByUserIdUpdatedAfterOrderByUpdatedAtDesc(
                eq(userId), thresholdCaptor.capture(), eq(cursorQuery)
        );
        long daysBack = ChronoUnit.DAYS.between(thresholdCaptor.getValue(), Instant.now());
        assertEquals(30, daysBack);
        verify(IChatPersistencePort, never()).findSessionsByUserIdOrderByUpdatedAtDesc(any(), any());
    }

    @Test
    void listSessionsReturnsFullHistoryWhenPlanHasNoWindow() {
        UUID userId = UUID.randomUUID();
        CursorQuery cursorQuery = new CursorQuery(0, 20);
        when(IChatEntitlementsPort.resolveEntitlements(userId)).thenReturn(new ChatEntitlements(25, null));
        when(IChatPersistencePort.findSessionsByUserIdOrderByUpdatedAtDesc(userId, cursorQuery))
                .thenReturn(new CursorResult<>(List.of(), null));

        chatService.listSessions(userId, cursorQuery);

        verify(IChatPersistencePort).findSessionsByUserIdOrderByUpdatedAtDesc(userId, cursorQuery);
        verify(IChatPersistencePort, never())
                .findSessionsByUserIdUpdatedAfterOrderByUpdatedAtDesc(any(), any(), any());
    }

    @Test
    void rateMessageRejectsMissingRequestOrRatingBeforePersistenceLookup() {
        UUID userId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        InvalidChatRequestException missingRequest = assertThrows(
                InvalidChatRequestException.class,
                () -> chatService.rateMessage(userId, messageId, null)
        );
        InvalidChatRequestException missingRating = assertThrows(
                InvalidChatRequestException.class,
                () -> chatService.rateMessage(userId, messageId, new ChatRateMessageRequest(null, "comment"))
        );

        assertEquals("rating_required", missingRequest.error().code());
        assertEquals("rating_required", missingRating.error().code());
        verify(IChatPersistencePort, never()).findMessageById(any());
        verifyNoInteractions(chatAccessPolicy);
    }
}
