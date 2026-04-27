package com.legalfam.backend.chat.application.port.in;

import com.legalfam.backend.chat.application.dto.ChatMessageResponse;
import com.legalfam.backend.chat.application.dto.ChatRateMessageRequest;
import com.legalfam.backend.chat.application.dto.ChatSendAcceptedResponse;
import com.legalfam.backend.chat.application.dto.ChatSessionResponse;
import java.util.List;
import java.util.UUID;

public interface ChatUseCase {
    ChatSendAcceptedResponse send(UUID userId, String messageInput, UUID sessionId);

    ChatSessionResponse createSession(UUID userId);

    List<ChatSessionResponse> listSessions(UUID userId);

    List<ChatMessageResponse> listMessages(UUID userId, UUID sessionId);

    void rateMessage(UUID userId, UUID messageId, ChatRateMessageRequest request);

    void assertSessionOwnershipExists(UUID userId, UUID sessionId);
}
