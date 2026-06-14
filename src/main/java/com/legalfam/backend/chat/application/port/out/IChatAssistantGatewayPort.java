package com.legalfam.backend.chat.application.port.out;

import com.legalfam.backend.chat.application.dto.ChatAssistantGatewayResponse;
import java.util.UUID;

public interface IChatAssistantGatewayPort {
    ChatAssistantGatewayResponse sendMessage(String message, UUID sessionId);
}
