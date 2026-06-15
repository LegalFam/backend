package com.legalfam.backend.chat.application.port.out;

import com.legalfam.backend.chat.application.dto.ChatAssistantGatewayResponse;
import com.legalfam.backend.chat.application.dto.ChatPreviousMessage;
import java.util.List;
import java.util.UUID;

public interface IChatAssistantGatewayPort {
    ChatAssistantGatewayResponse sendMessage(String message, UUID sessionId, List<ChatPreviousMessage> previousMessages);
}
