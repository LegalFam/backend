package com.legalfam.backend.chat.application.port.in;

import com.legalfam.backend.chat.application.dto.ChatAssistantErrorDispatch;
import com.legalfam.backend.chat.application.dto.ChatAssistantGatewayResponse;
import com.legalfam.backend.chat.application.dto.ChatAssistantMessageDispatch;
import com.legalfam.backend.chat.domain.model.ChatLanguage;
import java.util.UUID;

public interface IChatAssistantPersistenceUseCase {
    /**
     * Con {@code takeOver} un mensaje que ya figura en proceso se retoma: solo lo pide la
     * reentrega del broker, que ocurre cuando la instancia que lo procesaba murio.
     */
    boolean markUserMessageProcessing(UUID userMessageId, boolean takeOver);

    /**
     * Persiste la respuesta del asistente. Recibe la respuesta completa del gateway en vez
     * de sus piezas sueltas porque ahora trae tambien las versiones traducidas y el espanol
     * del mensaje del usuario, que hay que guardar en el mismo transaccional.
     */
    ChatAssistantMessageDispatch persistAssistantMessage(
            UUID chatSessionId,
            UUID userMessageId,
            ChatAssistantGatewayResponse response,
            ChatLanguage language
    );

    ChatAssistantErrorDispatch persistAssistantFailure(
            UUID chatSessionId,
            UUID userMessageId,
            String errorCode,
            String errorMessage
    );
}
