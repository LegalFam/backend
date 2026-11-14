package com.legalfam.backend.chat.infrastructure.adapter.out;

import com.legalfam.backend.chat.application.event.ChatAssistantDraftEvent;
import com.legalfam.backend.chat.application.event.ChatAssistantMessageEvent;
import com.legalfam.backend.chat.application.event.ChatAssistantErrorEvent;
import com.legalfam.backend.chat.application.port.out.IChatAssistantDeliveryPort;
import com.legalfam.backend.chat.infrastructure.config.ChatSseProperties;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class SseChatAssistantDeliveryAdapter implements IChatAssistantDeliveryPort {

    private static final Logger log = LoggerFactory.getLogger(SseChatAssistantDeliveryAdapter.class);
    private static final long MIN_INTERVAL_MS = 1000L;

    // Varias pestañas pueden mirar la misma consulta: cada una conserva su propia conexión.
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Set<SseEmitter>>> emittersByUser = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    private final long emitterTimeoutMs;

    public SseChatAssistantDeliveryAdapter(ChatSseProperties properties) {
        this.emitterTimeoutMs = Math.max(properties.safeEmitterTimeoutMs(), MIN_INTERVAL_MS);
        long safeHeartbeatIntervalMs = Math.max(properties.safeHeartbeatIntervalMs(), MIN_INTERVAL_MS);
        heartbeatExecutor.scheduleAtFixedRate(
                this::sendHeartbeatsSafely,
                safeHeartbeatIntervalMs,
                safeHeartbeatIntervalMs,
                TimeUnit.MILLISECONDS
        );
    }

    public SseEmitter subscribe(UUID userId, UUID sessionId) {
        SseEmitter emitter = new SseEmitter(emitterTimeoutMs);
        emittersByUser
                .computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(sessionId, ignored -> ConcurrentHashMap.newKeySet())
                .add(emitter);

        emitter.onCompletion(() -> removeEmitter(userId, sessionId, emitter));
        emitter.onTimeout(() -> {
            removeEmitter(userId, sessionId, emitter);
            emitter.complete();
        });
        emitter.onError(ex -> removeEmitter(userId, sessionId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data("connected"));
        } catch (IOException | IllegalStateException ex) {
            removeEmitter(userId, sessionId, emitter);
            emitter.completeWithError(ex);
        }

        return emitter;
    }

    @Override
    public boolean dispatchAssistantMessage(UUID userId, UUID sessionId, ChatAssistantMessageEvent event) {
        return sendToSession(userId, sessionId, () -> SseEmitter.event()
                .name("assistant_message")
                .id(event.messageId().toString())
                .data(event));
    }

    @Override
    public boolean dispatchAssistantError(UUID userId, UUID sessionId, ChatAssistantErrorEvent event) {
        return sendToSession(userId, sessionId, () -> SseEmitter.event()
                .name("assistant_error")
                .id(event.messageId().toString())
                .data(event));
    }

    @Override
    public boolean dispatchAssistantDraft(ChatAssistantDraftEvent event) {
        // El borrador se genera en la instancia que llama a n8n, que no conoce al usuario: se
        // busca la suscripcion por sesion. La sesion es de un solo usuario y el endpoint de
        // suscripcion ya valida la propiedad, asi que el borrador no puede cruzar cuentas.
        // Si el usuario esta conectado a otra instancia no lo ve, y no pasa nada: el mensaje
        // definitivo llega igual por el outbox.
        for (Map.Entry<UUID, ConcurrentHashMap<UUID, Set<SseEmitter>>> entry : emittersByUser.entrySet()) {
            if (entry.getValue().containsKey(event.sessionId())) {
                return sendToSession(entry.getKey(), event.sessionId(),
                        () -> SseEmitter.event().name("assistant_draft").data(event));
            }
        }
        return false;
    }

    @PreDestroy
    void shutdown() {
        heartbeatExecutor.shutdownNow();
    }

    private void sendHeartbeatsSafely() {
        try {
            sendHeartbeats();
        } catch (RuntimeException ex) {
            log.debug("Failed to emit SSE heartbeat", ex);
        }
    }

    private void sendHeartbeats() {
        emittersByUser.forEach((userId, userEmitters) ->
                userEmitters.keySet().forEach(sessionId ->
                        sendToSession(userId, sessionId, () -> SseEmitter.event().name("heartbeat").data("ping"))));
    }

    private boolean sendToSession(UUID userId, UUID sessionId, Supplier<SseEmitter.SseEventBuilder> event) {
        Map<UUID, Set<SseEmitter>> userEmitters = emittersByUser.get(userId);
        Set<SseEmitter> emitters = userEmitters == null ? null : userEmitters.get(sessionId);
        if (emitters == null || emitters.isEmpty()) {
            log.info("No active SSE emitter found for userId={} sessionId={}", userId, sessionId);
            return false;
        }

        boolean delivered = false;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(event.get());
                delivered = true;
            } catch (IOException | IllegalStateException ex) {
                log.debug("Removing closed SSE emitter for userId={} sessionId={}: {}", userId, sessionId, ex.getMessage());
                removeEmitter(userId, sessionId, emitter);
            }
        }
        return delivered;
    }

    private void removeEmitter(UUID userId, UUID sessionId, SseEmitter emitter) {
        emittersByUser.computeIfPresent(userId, (ignoredUserId, userEmitters) -> {
            userEmitters.computeIfPresent(sessionId, (ignoredSessionId, emitters) -> {
                emitters.remove(emitter);
                return emitters.isEmpty() ? null : emitters;
            });
            return userEmitters.isEmpty() ? null : userEmitters;
        });
    }
}
