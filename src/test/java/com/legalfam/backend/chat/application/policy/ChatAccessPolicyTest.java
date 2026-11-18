package com.legalfam.backend.chat.application.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.legalfam.backend.chat.application.port.out.IChatPersistencePort;
import com.legalfam.backend.chat.application.port.out.IChatUserLookupPort;
import com.legalfam.backend.chat.domain.exception.ChatAccessDeniedException;
import com.legalfam.backend.chat.domain.exception.ChatNotFoundException;
import com.legalfam.backend.chat.domain.model.ChatLanguage;
import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatMessageRole;
import com.legalfam.backend.chat.domain.model.ChatSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatAccessPolicyTest {

    private final IChatPersistencePort persistence = mock(IChatPersistencePort.class);
    private final IChatUserLookupPort users = mock(IChatUserLookupPort.class);
    private final ChatAccessPolicy policy = new ChatAccessPolicy(persistence, users);

    private final UUID ownerId = UUID.randomUUID();
    private final ChatSession session = ChatSession.restore(
            UUID.randomUUID(), ownerId, "Consulta", Instant.now(), Instant.now()
    );

    @Test
    void acceptsExistingUserAndRejectsUnknownOne() {
        UUID unknown = UUID.randomUUID();
        when(users.existsById(ownerId)).thenReturn(true);

        policy.assertUserExists(ownerId);
        assertThatThrownBy(() -> policy.assertUserExists(unknown)).isInstanceOf(ChatAccessDeniedException.class);
    }

    @Test
    void returnsSessionToItsOwner() {
        when(persistence.findSessionById(session.getId())).thenReturn(Optional.of(session));

        assertThat(policy.requireSessionOwner(ownerId, session.getId())).isSameAs(session);
    }

    @Test
    void rejectsSessionOfAnotherUser() {
        when(persistence.findSessionById(session.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> policy.requireSessionOwner(UUID.randomUUID(), session.getId()))
                .isInstanceOf(ChatAccessDeniedException.class);
    }

    @Test
    void reportsMissingSession() {
        UUID missing = UUID.randomUUID();
        when(persistence.findSessionById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> policy.requireSessionOwner(ownerId, missing))
                .isInstanceOf(ChatNotFoundException.class);
    }

    @Test
    void checksOwnershipThroughTheMessageSession() {
        ChatMessage message = ChatMessage.restore(
                UUID.randomUUID(), session.getId(), ChatMessageRole.USER, "Hola", ChatLanguage.ES,
                null, null, null, null, null, null, null, null, List.of(), List.of(), null, null, Instant.now()
        );
        when(persistence.findSessionById(session.getId())).thenReturn(Optional.of(session));

        assertThat(policy.requireMessageSessionOwner(ownerId, message)).isSameAs(session);
        assertThatThrownBy(() -> policy.requireMessageSessionOwner(UUID.randomUUID(), message))
                .isInstanceOf(ChatAccessDeniedException.class);
    }
}
