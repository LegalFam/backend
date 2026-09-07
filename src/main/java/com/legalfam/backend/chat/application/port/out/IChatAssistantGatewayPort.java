package com.legalfam.backend.chat.application.port.out;

import com.legalfam.backend.chat.application.dto.ChatAssistantGatewayResponse;
import com.legalfam.backend.chat.application.dto.ChatPreviousMessage;
import com.legalfam.backend.chat.domain.model.ChatLanguage;
import java.util.List;
import java.util.UUID;

public interface IChatAssistantGatewayPort {
    /**
     * @param message          consulta del usuario, tal como la escribio.
     * @param previousMessages historial, siempre en espanol.
     * @param language         lengua en la que el usuario espera leer la respuesta.
     */
    ChatAssistantGatewayResponse sendMessage(
            String message,
            UUID sessionId,
            List<ChatPreviousMessage> previousMessages,
            ChatLanguage language
    );
}
