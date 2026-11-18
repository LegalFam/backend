package com.legalfam.backend.chat.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.legalfam.backend.chat.application.dto.ChatEntitlements;
import com.legalfam.backend.chat.application.dto.ChatMessageResponse;
import com.legalfam.backend.chat.application.dto.ChatProcessingStatusResponse;
import com.legalfam.backend.chat.application.dto.ChatRateMessageRequest;
import com.legalfam.backend.chat.application.dto.ChatSessionResponse;
import com.legalfam.backend.chat.application.dto.ChatUpdateSessionRequest;
import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import com.legalfam.backend.chat.application.policy.ChatAccessPolicy;
import com.legalfam.backend.chat.application.policy.ChatPrivacyPolicy;
import com.legalfam.backend.chat.application.port.out.IChatEntitlementsPort;
import com.legalfam.backend.chat.application.port.out.IChatEventPublisherPort;
import com.legalfam.backend.chat.application.port.out.IChatPersistencePort;
import com.legalfam.backend.chat.application.port.out.IChatTokenPort;
import com.legalfam.backend.chat.application.service.mapper.ChatMessageResponseMapper;
import com.legalfam.backend.chat.domain.exception.ChatNotFoundException;
import com.legalfam.backend.chat.domain.exception.InvalidChatRequestException;
import com.legalfam.backend.chat.domain.model.ChatCitation;
import com.legalfam.backend.chat.domain.model.ChatLanguage;
import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessing;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessingStatus;
import com.legalfam.backend.chat.domain.model.ChatMessageRole;
import com.legalfam.backend.chat.domain.model.ChatOutboxEvent;
import com.legalfam.backend.chat.domain.model.ChatOutboxEventStatus;
import com.legalfam.backend.chat.domain.model.ChatSession;
import com.legalfam.backend.common.cursor.CursorQuery;
import com.legalfam.backend.common.cursor.CursorResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChatServiceSessionAndMessageTest {

    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

    private final IChatPersistencePort persistence = mock(IChatPersistencePort.class);
    private final IChatEventPublisherPort publisher = mock(IChatEventPublisherPort.class);
    private final IChatTokenPort tokens = mock(IChatTokenPort.class);
    private final IChatEntitlementsPort entitlements = mock(IChatEntitlementsPort.class);
    private final ChatAccessPolicy access = mock(ChatAccessPolicy.class);
    private final ChatService service = new ChatService(
            persistence, publisher, tokens, entitlements, access, mock(ChatPrivacyPolicy.class),
            new ChatMessageResponseMapper()
    );

    private final UUID userId = UUID.randomUUID();
    private final ChatSession session = ChatSession.restore(UUID.randomUUID(), userId, "Consulta", NOW, NOW);

    @Test
    void reportsIdleOrActiveProcessing() {
        assertThat(service.getProcessingStatus(userId)).isEqualTo(ChatProcessingStatusResponse.idle());

        ChatMessage userMessage = message(ChatMessageRole.USER);
        ChatMessageProcessing processing = ChatMessageProcessing.restore(
                UUID.randomUUID(), userMessage.getId(), ChatMessageProcessingStatus.PROCESSING,
                null, null, NOW, null, NOW, NOW.plusSeconds(5)
        );
        when(persistence.findActiveMessageProcessingByUserId(userId)).thenReturn(Optional.of(processing));
        when(persistence.findMessageById(userMessage.getId())).thenReturn(Optional.of(userMessage));

        ChatProcessingStatusResponse status = service.getProcessingStatus(userId);

        assertThat(status.processing()).isTrue();
        assertThat(status.chatSessionId()).isEqualTo(session.getId());
        assertThat(status.status()).isEqualTo("PROCESSING");
        assertThat(status.updatedAt()).isEqualTo(NOW.plusSeconds(5));
    }

    @Test
    void createsRenamesAndDeletesSessions() {
        when(persistence.saveSession(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(access.requireSessionOwner(userId, session.getId())).thenReturn(session);

        ChatSessionResponse created = service.createSession(userId);
        ChatSessionResponse renamed = service.updateSession(userId, session.getId(), new ChatUpdateSessionRequest("  Tenencia  "));
        service.deleteSession(userId, session.getId());
        service.assertSessionOwnershipExists(userId, session.getId());

        assertThat(created.createdAt()).isNotNull();
        assertThat(renamed.title()).isEqualTo("Tenencia");
        verify(access).assertUserExists(userId);
        verify(persistence).deleteSessionById(session.getId());
        assertThatThrownBy(() -> service.updateSession(userId, session.getId(), null))
                .isInstanceOf(InvalidChatRequestException.class);
    }

    @Test
    void listsMessagesOldestFirstWithCitationsAndReceipts() {
        ChatMessage newer = message(ChatMessageRole.ASSISTANT);
        ChatMessage older = message(ChatMessageRole.USER);
        ChatCitation citation = ChatCitation.restore(UUID.randomUUID(), newer.getId(), "Código Civil", "s", null,
                null, null, "Art. 472", null, "ARTICLE");
        ChatOutboxEvent event = outbox(newer.getId());
        when(access.requireSessionOwner(userId, session.getId())).thenReturn(session);
        when(persistence.findMessagesBySessionIdOrderByCreatedAtDesc(eq(session.getId()), any()))
                .thenReturn(new CursorResult<>(List.of(newer, older), 40));
        when(persistence.findCitationsByMessageIdsOrderByMessageIdAndId(List.of(older.getId(), newer.getId())))
                .thenReturn(List.of(citation));
        when(persistence.findOutboxEventsByAggregateIds(List.of(older.getId(), newer.getId())))
                .thenReturn(List.of(event));

        CursorResult<ChatMessageResponse> page = service.listMessages(userId, session.getId(), CursorQuery.of(20, 20));

        assertThat(page.nextCursor()).isEqualTo(40);
        assertThat(page.content()).extracting(ChatMessageResponse::id).containsExactly(older.getId(), newer.getId());
        assertThat(page.content().get(1).citations()).hasSize(1);
        assertThat(page.content().get(1).receiptStatus()).isEqualTo("PENDING");
        assertThat(page.content().get(0).citations()).isEmpty();
    }

    @Test
    void skipsLookupsForAnEmptyMessagePage() {
        when(access.requireSessionOwner(userId, session.getId())).thenReturn(session);
        when(persistence.findMessagesBySessionIdOrderByCreatedAtDesc(eq(session.getId()), any()))
                .thenReturn(new CursorResult<>(List.of(), null));

        assertThat(service.listMessages(userId, session.getId(), CursorQuery.of(null, 20)).content()).isEmpty();
        verify(persistence, never()).findCitationsByMessageIdsOrderByMessageIdAndId(any());
    }

    @Test
    void storesRatingOnAnOwnedAssistantMessage() {
        ChatMessage answer = message(ChatMessageRole.ASSISTANT);
        when(persistence.findMessageById(answer.getId())).thenReturn(Optional.of(answer));

        service.rateMessage(userId, answer.getId(), new ChatRateMessageRequest(4, "  Útil  "));

        verify(access).requireMessageSessionOwner(userId, answer);
        verify(persistence).saveMessage(answer);
        assertThat(answer.getRating()).isEqualTo(4);
        assertThat(answer.getFeedbackComment()).isEqualTo("Útil");
        assertThatThrownBy(() -> service.rateMessage(userId, UUID.randomUUID(), new ChatRateMessageRequest(5, null)))
                .isInstanceOf(ChatNotFoundException.class);
    }

    @Test
    void marksAssistantMessageAsRead() {
        ChatMessage answer = message(ChatMessageRole.ASSISTANT);
        ChatOutboxEvent event = outbox(answer.getId());
        when(persistence.findMessageById(answer.getId())).thenReturn(Optional.of(answer));
        when(access.requireSessionOwner(userId, session.getId())).thenReturn(session);
        when(persistence.findOutboxEventByAggregateIdForUpdate(answer.getId())).thenReturn(Optional.of(event));

        service.confirmAssistantReceipt(userId, answer.getId());

        assertThat(event.getStatus()).isEqualTo(ChatOutboxEventStatus.READ);
        assertThat(event.getReadAt()).isNotNull();
        verify(persistence).saveOutboxEvent(event);
        verify(persistence).saveSession(session);
    }

    @Test
    void rejectsReceiptsForUserMessagesOrMissingEvents() {
        ChatMessage question = message(ChatMessageRole.USER);
        ChatMessage answer = message(ChatMessageRole.ASSISTANT);
        when(persistence.findMessageById(question.getId())).thenReturn(Optional.of(question));
        when(persistence.findMessageById(answer.getId())).thenReturn(Optional.of(answer));
        when(access.requireSessionOwner(userId, session.getId())).thenReturn(session);

        assertThatThrownBy(() -> service.confirmAssistantReceipt(userId, question.getId()))
                .isInstanceOf(InvalidChatRequestException.class);
        assertThatThrownBy(() -> service.confirmAssistantReceipt(userId, answer.getId()))
                .isInstanceOf(ChatNotFoundException.class);
        assertThatThrownBy(() -> service.confirmAssistantReceipt(userId, UUID.randomUUID()))
                .isInstanceOf(ChatNotFoundException.class);
    }

    @Test
    void sendsEmptyContextForMessagesWithoutContent() {
        when(access.requireSessionOwner(userId, session.getId())).thenReturn(session);
        when(persistence.findActiveMessageProcessingByUserId(userId)).thenReturn(Optional.empty());
        when(tokens.hasChatTokensAvailable(userId)).thenReturn(true);
        when(entitlements.resolveEntitlements(userId)).thenReturn(new ChatEntitlements(10, null));
        ChatMessage blank = ChatMessage.restore(UUID.randomUUID(), session.getId(), ChatMessageRole.ASSISTANT, null,
                ChatLanguage.ES, null, null, null, null, null, null, null, null, List.of(), List.of(), null, null, NOW);
        when(persistence.findRecentMessagesForAssistantContext(eq(session.getId()), anyInt())).thenReturn(List.of(blank));
        when(persistence.saveMessage(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.send(userId, "hola", session.getId(), "es");

        ArgumentCaptor<ChatMessageQueuedEvent> event = ArgumentCaptor.forClass(ChatMessageQueuedEvent.class);
        verify(publisher).publishMessageQueued(event.capture());
        assertThat(event.getValue().previousMessages()).singleElement()
                .satisfies(previous -> assertThat(previous.content()).isEmpty());
    }

    private ChatMessage message(ChatMessageRole role) {
        return ChatMessage.restore(UUID.randomUUID(), session.getId(), role, "texto", ChatLanguage.ES, null, null,
                null, null, null, null, null, null, List.of(), List.of(), null, null, NOW);
    }

    private ChatOutboxEvent outbox(UUID messageId) {
        return ChatOutboxEvent.restore(UUID.randomUUID(), "ASSISTANT_MESSAGE_READY", messageId, session.getId(), "{}",
                ChatOutboxEventStatus.PENDING, 0, NOW, null, null, null, NOW, NOW);
    }
}
