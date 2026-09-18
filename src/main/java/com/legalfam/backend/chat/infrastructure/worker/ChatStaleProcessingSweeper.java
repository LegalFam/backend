package com.legalfam.backend.chat.infrastructure.worker;

import com.legalfam.backend.chat.application.port.in.IChatAssistantPersistenceUseCase;
import com.legalfam.backend.chat.application.port.out.IChatPersistencePort;
import com.legalfam.backend.chat.domain.exception.ChatApiError;
import com.legalfam.backend.chat.infrastructure.config.N8nProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * El pedido al agente vive en memoria: si la instancia muere mientras espera la respuesta, el
 * mensaje queda en proceso para siempre y bloquea los envíos del usuario. Pasado el timeout de
 * n8n ninguna instancia puede seguir con él, así que se cierra con un error que el usuario ve.
 */
@Service
public class ChatStaleProcessingSweeper {

    private static final Logger log = LoggerFactory.getLogger(ChatStaleProcessingSweeper.class);
    private static final Duration MARGIN = Duration.ofMinutes(1);

    private final IChatPersistencePort IChatPersistencePort;
    private final IChatAssistantPersistenceUseCase IChatAssistantPersistenceUseCase;
    private final Duration staleAfter;

    public ChatStaleProcessingSweeper(
            IChatPersistencePort IChatPersistencePort,
            IChatAssistantPersistenceUseCase IChatAssistantPersistenceUseCase,
            N8nProperties n8nProperties
    ) {
        this.IChatPersistencePort = IChatPersistencePort;
        this.IChatAssistantPersistenceUseCase = IChatAssistantPersistenceUseCase;
        this.staleAfter = Duration.ofMillis(n8nProperties.safeTimeoutMs()).plus(MARGIN);
    }

    @Scheduled(fixedDelayString = "${app.chat.processing.sweep-delay-ms:60000}")
    public void failStaleProcessing() {
        for (UUID userMessageId : IChatPersistencePort.findActiveMessageProcessingUpdatedBefore(Instant.now().minus(staleAfter))) {
            IChatPersistencePort.findMessageById(userMessageId).ifPresent(message -> {
                log.warn("Failing chat message left in processing userMessageId={}", userMessageId);
                IChatAssistantPersistenceUseCase.persistAssistantFailure(
                        message.getChatSessionId(),
                        userMessageId,
                        ChatApiError.UPSTREAM_TIMEOUT.code(),
                        ChatApiError.UPSTREAM_TIMEOUT.message()
                );
            });
        }
    }
}
