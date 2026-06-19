package com.legalfam.backend.chat.domain.policy;

import com.legalfam.backend.chat.domain.exception.PendingAssistantMessageException;

public final class ChatSendPolicy {

    private ChatSendPolicy() {}

    public static void assertCanSend(boolean hasActiveMessageProcessing, boolean hasUnreadAssistantMessage) {
        if (hasActiveMessageProcessing) {
            throw PendingAssistantMessageException.processingPending();
        }
        if (hasUnreadAssistantMessage) {
            throw PendingAssistantMessageException.receiptPending();
        }
    }
}
