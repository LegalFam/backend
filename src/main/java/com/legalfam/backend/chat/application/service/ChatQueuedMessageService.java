package com.legalfam.backend.chat.application.service;

import com.legalfam.backend.chat.application.dto.ChatAssistantGatewayResponse;
import com.legalfam.backend.chat.application.dto.ChatAssistantMessageDispatch;
import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import com.legalfam.backend.chat.application.port.in.IChatAssistantPersistenceUseCase;
import com.legalfam.backend.chat.application.port.in.IChatQueuedMessageUseCase;
import com.legalfam.backend.chat.application.port.out.IChatAssistantGatewayPort;
import com.legalfam.backend.chat.domain.exception.ChatApiError;
import com.legalfam.backend.chat.domain.exception.ChatUpstreamException;
import com.legalfam.backend.chat.domain.model.ChatLanguage;
import com.legalfam.backend.common.error.ApiErrorDescriptor;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChatQueuedMessageService implements IChatQueuedMessageUseCase {

    private static final Logger log = LoggerFactory.getLogger(ChatQueuedMessageService.class);

    private final IChatAssistantGatewayPort IChatAssistantGatewayPort;
    private final IChatAssistantPersistenceUseCase IChatAssistantPersistenceUseCase;

    public ChatQueuedMessageService(
            IChatAssistantGatewayPort IChatAssistantGatewayPort,
            IChatAssistantPersistenceUseCase IChatAssistantPersistenceUseCase
    ) {
        this.IChatAssistantGatewayPort = IChatAssistantGatewayPort;
        this.IChatAssistantPersistenceUseCase = IChatAssistantPersistenceUseCase;
    }

    @Override
    public void process(ChatMessageQueuedEvent event) {
        UUID chatSessionId = event.chatSessionId();
        UUID userMessageId = event.userMessageId();
        String userMessageInput = event.userMessageInput();
        ChatLanguage language = event.resolvedLanguage();

        if (!IChatAssistantPersistenceUseCase.markUserMessageProcessing(userMessageId)) {
            log.debug("Ignoring duplicate or terminal chat event userMessageId={}", userMessageId);
            return;
        }

        ChatAssistantGatewayResponse response;
        try {
            response = IChatAssistantGatewayPort.sendMessage(
                    userMessageInput,
                    chatSessionId,
                    event.previousMessages(),
                    language
            );
        } catch (ChatUpstreamException ex) {
            log.warn("Assistant gateway call failed for chatSessionId={}: {}", chatSessionId, ex.getMessage());
            persistAndDispatchFailure(
                    chatSessionId,
                    userMessageId,
                    ex.error()
            );
            return;
        } catch (RuntimeException ex) {
            log.warn("Assistant gateway call failed for chatSessionId={}: {}", chatSessionId, ex.getMessage());
            persistAndDispatchFailure(
                    chatSessionId,
                    userMessageId,
                    ChatApiError.UPSTREAM_ERROR
            );
            return;
        }

        if (response == null || isBlank(response.message())) {
            log.warn("Assistant gateway response has empty message for chatSessionId={}", chatSessionId);
            persistAndDispatchFailure(
                    chatSessionId,
                    userMessageId,
                    ChatApiError.UPSTREAM_EMPTY_RESPONSE
            );
            return;
        }

        ChatAssistantMessageDispatch dispatch = IChatAssistantPersistenceUseCase.persistAssistantMessage(
                chatSessionId,
                userMessageId,
                response,
                language
        );
        if (dispatch == null) {
            return;
        }
    }

    private void persistAndDispatchFailure(
            UUID chatSessionId,
            UUID userMessageId,
            ApiErrorDescriptor error
    ) {
        IChatAssistantPersistenceUseCase.persistAssistantFailure(
                chatSessionId,
                userMessageId,
                error.code(),
                error.message()
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
