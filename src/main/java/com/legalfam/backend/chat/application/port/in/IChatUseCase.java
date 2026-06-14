package com.legalfam.backend.chat.application.port.in;

import com.legalfam.backend.chat.application.dto.ChatMessageResponse;
import com.legalfam.backend.chat.application.dto.ChatRateMessageRequest;
import com.legalfam.backend.chat.application.dto.ChatSendAcceptedResponse;
import com.legalfam.backend.chat.application.dto.ChatSessionResponse;
import com.legalfam.backend.chat.application.dto.ChatUpdateSessionRequest;
import com.legalfam.backend.common.cursor.CursorQuery;
import com.legalfam.backend.common.cursor.CursorResult;
import java.util.UUID;

public interface IChatUseCase {
    ChatSendAcceptedResponse send(UUID userId, String messageInput, UUID sessionId);

    ChatSessionResponse createSession(UUID userId);

    ChatSessionResponse updateSession(UUID userId, UUID sessionId, ChatUpdateSessionRequest request);

    void deleteSession(UUID userId, UUID sessionId);

    CursorResult<ChatSessionResponse> listSessions(UUID userId, CursorQuery cursorQuery);

    CursorResult<ChatMessageResponse> listMessages(UUID userId, UUID sessionId, CursorQuery cursorQuery);

    void rateMessage(UUID userId, UUID messageId, ChatRateMessageRequest request);

    void confirmAssistantReceipt(UUID userId, UUID messageId);

    void assertSessionOwnershipExists(UUID userId, UUID sessionId);
}
