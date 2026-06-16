package com.legalfam.backend.chat.domain.policy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.legalfam.backend.chat.domain.exception.PendingAssistantMessageException;
import org.junit.jupiter.api.Test;

class ChatSendPolicyTest {

    @Test
    void assertCanSendAllowsIdleSessionWithoutUnreadAssistantMessage() {
        assertDoesNotThrow(() -> ChatSendPolicy.assertCanSend(false, false));
    }

    @Test
    void assertCanSendRejectsActiveMessageProcessing() {
        assertThrows(
                PendingAssistantMessageException.class,
                () -> ChatSendPolicy.assertCanSend(true, false)
        );
    }

    @Test
    void assertCanSendRejectsUnreadAssistantMessage() {
        assertThrows(
                PendingAssistantMessageException.class,
                () -> ChatSendPolicy.assertCanSend(false, true)
        );
    }
}
