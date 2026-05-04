package com.legalfam.backend.chat.application.port.in;

import com.legalfam.backend.chat.application.dto.ChatAssistantErrorDispatch;
import com.legalfam.backend.chat.application.dto.ChatAssistantMessageDispatch;
import com.legalfam.backend.chat.application.dto.ChatCitationResponse;
import java.util.List;
import java.util.UUID;

public interface ChatAssistantPersistenceUseCase {
    ChatAssistantMessageDispatch persistAssistantMessage(
            UUID chatSessionId,
            String assistantMessageText,
            List<ChatCitationResponse> citations
    );

    ChatAssistantErrorDispatch persistAssistantFailure(
            UUID chatSessionId,
            UUID userMessageId,
            String errorCode,
            String errorMessage
    );
}
