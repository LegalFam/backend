package com.legalfam.backend.chat.application.service;

import com.legalfam.backend.chat.application.dto.ChatAssistantGatewayResponse;
import com.legalfam.backend.chat.application.dto.ChatAssistantMessageDispatch;
import com.legalfam.backend.chat.application.event.ChatAssistantDraftEvent;
import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import com.legalfam.backend.chat.application.port.in.IChatAssistantPersistenceUseCase;
import com.legalfam.backend.chat.application.port.in.IChatQueuedMessageUseCase;
import com.legalfam.backend.chat.application.port.out.IChatAssistantDeliveryPort;
import com.legalfam.backend.chat.application.port.out.IChatAssistantDraftListener;
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
    /** Como mucho un borrador por intervalo: el texto llega en decenas de chunks por segundo. */
    static final long DRAFT_MIN_INTERVAL_MS = 120L;

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
                    language,
                    draftListener(chatSessionId, userMessageId, language)
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

    /**
     * Reenvia al chat la respuesta mientras se escribe. Solo en espanol: el flujo redacta en
     * espanol y traduce al final, asi que en quechua o aymara el borrador mostraria un texto
     * que despues cambia de idioma.
     */
    private IChatAssistantDraftListener draftListener(UUID chatSessionId, UUID userMessageId, ChatLanguage language) {
        if (language != null && !language.isSpanish()) {
            return IChatAssistantDraftListener.NONE;
        }
        return new ThrottledDraftListener(chatSessionId, userMessageId);
    }

    private final class ThrottledDraftListener implements IChatAssistantDraftListener {

        private final UUID chatSessionId;
        private final UUID userMessageId;
        private long sequence;
        private long lastSentAtMs = Long.MIN_VALUE;
        private int lastSentLength;

        private ThrottledDraftListener(UUID chatSessionId, UUID userMessageId) {
            this.chatSessionId = chatSessionId;
            this.userMessageId = userMessageId;
        }

        @Override
        public void onDraft(String textSoFar) {
            String text = textSoFar == null ? "" : textSoFar;
            long now = System.currentTimeMillis();
            // Un texto mas corto es un reintento del flujo: se manda siempre, para que el chat
            // no siga mostrando lo descartado. El resto se espacia; el ultimo tramo no hace
            // falta, porque el mensaje definitivo lo reemplaza.
            boolean restarted = text.length() < lastSentLength;
            if (!restarted && lastSentAtMs != Long.MIN_VALUE && now - lastSentAtMs < DRAFT_MIN_INTERVAL_MS) {
                return;
            }
            lastSentAtMs = now;
            lastSentLength = text.length();
            IChatAssistantDeliveryPort.dispatchAssistantDraft(
                    new ChatAssistantDraftEvent(chatSessionId, userMessageId, text, ++sequence)
            );
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
