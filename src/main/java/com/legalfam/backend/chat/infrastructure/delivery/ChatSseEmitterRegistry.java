package com.legalfam.backend.chat.infrastructure.delivery;

import com.legalfam.backend.chat.application.event.ChatAssistantMessageEvent;
import com.legalfam.backend.chat.application.event.ChatAssistantErrorEvent;
import com.legalfam.backend.chat.application.port.out.IChatAssistantDeliveryPort;
import com.legalfam.backend.chat.infrastructure.config.ChatSseProperties;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ChatSseEmitterRegistry implements IChatAssistantDeliveryPort {

    private static final Logger log = LoggerFactory.getLogger(ChatSseEmitterRegistry.class);
    private static final long MIN_INTERVAL_MS = 1000L;

    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, SseEmitter>> emittersByUser = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    private final long emitterTimeoutMs;

    public ChatSseEmitterRegistry(ChatSseProperties properties) {
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
        ConcurrentHashMap<UUID, SseEmitter> userEmitters = emittersByUser.computeIfAbsent(
                userId,
                ignored -> new ConcurrentHashMap<>()
        );
        SseEmitter previousEmitter = userEmitters.put(sessionId, emitter);
        if (previousEmitter != null) {
            previousEmitter.complete();
        }

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
        Map<UUID, SseEmitter> userEmitters = emittersByUser.get(userId);
        if (userEmitters == null) {
            log.info("No active SSE emitter found for userId={} sessionId={}", userId, sessionId);
            return false;
        }

        SseEmitter emitter = userEmitters.get(sessionId);
        if (emitter == null) {
            log.info("No active SSE emitter found for userId={} sessionId={}", userId, sessionId);
            return false;
        }

        try {
            emitter.send(SseEmitter.event()
                    .name("assistant_message")
                    .id(event.messageId().toString())
                    .data(event));
            return true;
        } catch (IOException | IllegalStateException ex) {
            log.info("Failed to dispatch SSE event for userId={} sessionId={}: {}", userId, sessionId, ex.getMessage());
            removeEmitter(userId, sessionId, emitter);
            return false;
        }
    }

    @Override
    public boolean dispatchAssistantError(UUID userId, UUID sessionId, ChatAssistantErrorEvent event) {
        Map<UUID, SseEmitter> userEmitters = emittersByUser.get(userId);
        if (userEmitters == null) {
            log.info("No active SSE emitter found for userId={} sessionId={}", userId, sessionId);
            return false;
        }

        SseEmitter emitter = userEmitters.get(sessionId);
        if (emitter == null) {
            log.info("No active SSE emitter found for userId={} sessionId={}", userId, sessionId);
            return false;
        }

        try {
            emitter.send(SseEmitter.event()
                    .name("assistant_error")
                    .id(event.messageId().toString())
                    .data(event));
            return true;
        } catch (IOException | IllegalStateException ex) {
            log.info("Failed to dispatch SSE error event for userId={} sessionId={}: {}", userId, sessionId, ex.getMessage());
            removeEmitter(userId, sessionId, emitter);
            return false;
        }
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
                userEmitters.forEach((sessionId, emitter) -> {
                    try {
                        emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
                    } catch (IOException | IllegalStateException ex) {
                        log.debug("Removing closed SSE emitter for userId={} sessionId={}: {}",
                                userId,
                                sessionId,
                                ex.getMessage());
                        removeEmitter(userId, sessionId, emitter);
                    }
                }));
    }

    private void removeEmitter(UUID userId, UUID sessionId, SseEmitter expectedEmitter) {
        emittersByUser.computeIfPresent(userId, (ignoredUserId, userEmitters) -> {
            userEmitters.computeIfPresent(sessionId, (ignoredSessionId, currentEmitter) ->
                    currentEmitter == expectedEmitter ? null : currentEmitter
            );
            return userEmitters.isEmpty() ? null : userEmitters;
        });
    }
}
