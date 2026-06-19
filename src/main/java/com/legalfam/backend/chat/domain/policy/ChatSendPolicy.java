package com.legalfam.backend.chat.domain.policy;

import com.legalfam.backend.chat.domain.exception.PendingAssistantMessageException;

public final class ChatSendPolicy {

    private ChatSendPolicy() {}

    public static void assertCanSend(boolean hasActiveMessageProcessing, boolean hasUnreadAssistantMessage) {
        if (hasActiveMessageProcessing) {
            throw new PendingAssistantMessageException("Message processing is already pending");
        }
        if (hasUnreadAssistantMessage) {
            throw new PendingAssistantMessageException("Assistant receipt confirmation is still pending for this session");
        }
    }
}
