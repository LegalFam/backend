package com.legalfam.backend.chat.application.service;

import com.legalfam.backend.chat.application.dto.ChatAssistantErrorDispatch;
import com.legalfam.backend.chat.application.dto.ChatAssistantGatewayResponse;
import com.legalfam.backend.chat.application.dto.ChatAssistantMessageDispatch;
import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import com.legalfam.backend.chat.application.port.in.IChatAssistantPersistenceUseCase;
import com.legalfam.backend.chat.application.port.in.IChatQueuedMessageUseCase;
import com.legalfam.backend.chat.application.port.out.IChatAssistantDeliveryPort;
import com.legalfam.backend.chat.application.port.out.IChatAssistantGatewayPort;
import com.legalfam.backend.chat.domain.exception.ChatUpstreamException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChatQueuedMessageService implements IChatQueuedMessageUseCase {

    private static final Logger log = LoggerFactory.getLogger(ChatQueuedMessageService.class);

    private final IChatAssistantGatewayPort IChatAssistantGatewayPort;
    private final IChatAssistantPersistenceUseCase IChatAssistantPersistenceUseCase;
    private final IChatAssistantDeliveryPort IChatAssistantDeliveryPort;

    public ChatQueuedMessageService(
            IChatAssistantGatewayPort IChatAssistantGatewayPort,
            IChatAssistantPersistenceUseCase IChatAssistantPersistenceUseCase,
            IChatAssistantDeliveryPort IChatAssistantDeliveryPort
    ) {
        this.IChatAssistantGatewayPort = IChatAssistantGatewayPort;
        this.IChatAssistantPersistenceUseCase = IChatAssistantPersistenceUseCase;
        this.IChatAssistantDeliveryPort = IChatAssistantDeliveryPort;
    }

    @Override
    public void process(ChatMessageQueuedEvent event) {
        UUID chatSessionId = event.chatSessionId();
        UUID userMessageId = event.userMessageId();
        String userMessageInput = event.userMessageInput();

        if (!IChatAssistantPersistenceUseCase.markUserMessageProcessing(userMessageId)) {
            log.debug("Ignoring duplicate or terminal chat event userMessageId={}", userMessageId);
            return;
        }

        ChatAssistantGatewayResponse response;
        try {
            response = IChatAssistantGatewayPort.sendMessage(userMessageInput, chatSessionId);
        } catch (ChatUpstreamException ex) {
            log.warn("Assistant gateway call failed for chatSessionId={}: {}", chatSessionId, ex.getMessage());
            persistAndDispatchFailure(
                    chatSessionId,
                    userMessageId,
                    ex.getCode(),
                    buildFailureMessage(ex.getCode())
            );
            return;
        } catch (RuntimeException ex) {
            log.warn("Assistant gateway call failed for chatSessionId={}: {}", chatSessionId, ex.getMessage());
            persistAndDispatchFailure(
                    chatSessionId,
                    userMessageId,
                    "UPSTREAM_ERROR",
                    buildFailureMessage("UPSTREAM_ERROR")
            );
            return;
        }

        if (response == null || isBlank(response.message())) {
            log.warn("Assistant gateway response has empty message for chatSessionId={}", chatSessionId);
            persistAndDispatchFailure(
                    chatSessionId,
                    userMessageId,
                    "UPSTREAM_EMPTY_RESPONSE",
                    buildFailureMessage("UPSTREAM_EMPTY_RESPONSE")
            );
            return;
        }

        ChatAssistantMessageDispatch dispatch = IChatAssistantPersistenceUseCase.persistAssistantMessage(
                chatSessionId,
                userMessageId,
                response.message(),
                response.citations(),
                response.metadata()
        );
        if (dispatch == null) {
            return;
        }
    }

    private void persistAndDispatchFailure(
            UUID chatSessionId,
            UUID userMessageId,
            String errorCode,
            String errorMessage
    ) {
        ChatAssistantErrorDispatch dispatch = IChatAssistantPersistenceUseCase.persistAssistantFailure(
                chatSessionId,
                userMessageId,
                errorCode,
                errorMessage
        );
        if (dispatch == null) {
            return;
        }
        IChatAssistantDeliveryPort.dispatchAssistantError(dispatch.userId(), dispatch.chatSessionId(), dispatch.event());
    }

    private String buildFailureMessage(String errorCode) {
        if ("UPSTREAM_TIMEOUT".equals(errorCode)) {
            return "No pude preparar la respuesta porque el servicio tardo demasiado. Puedes intentar nuevamente.";
        }
        if ("UPSTREAM_EMPTY_RESPONSE".equals(errorCode) || "UPSTREAM_INVALID_RESPONSE".equals(errorCode)) {
            return "No pude preparar una respuesta valida. Puedes intentar nuevamente.";
        }
        if ("AGENT_VALIDATION_FAILED".equals(errorCode)) {
            return "No pude validar la respuesta generada. Puedes intentar nuevamente.";
        }
        if ("UPSTREAM_NOT_CONFIGURED".equals(errorCode)) {
            return "El servicio de respuesta no esta disponible en este momento.";
        }
        return "No pude preparar la respuesta por un problema temporal. Puedes intentar nuevamente.";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
