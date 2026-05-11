package com.legalfam.backend.chat.infrastructure.sse;

import com.legalfam.backend.chat.application.event.ChatAssistantMessageEvent;
import com.legalfam.backend.chat.application.event.ChatAssistantErrorEvent;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ChatSseEmitterService {

    private static final Logger log = LoggerFactory.getLogger(ChatSseEmitterService.class);
    private static final long MIN_INTERVAL_MS = 1000L;

    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, SseEmitter>> emittersByUser = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    private final long emitterTimeoutMs;

    public ChatSseEmitterService(
            @Value("${app.chat.sse.emitter-timeout-ms:1800000}") long emitterTimeoutMs,
            @Value("${app.chat.sse.heartbeat-interval-ms:15000}") long heartbeatIntervalMs
    ) {
        this.emitterTimeoutMs = Math.max(emitterTimeoutMs, MIN_INTERVAL_MS);
        long safeHeartbeatIntervalMs = Math.max(heartbeatIntervalMs, MIN_INTERVAL_MS);
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
            emitter.completeWithError(ex);
            return false;
        }
    }

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
            emitter.completeWithError(ex);
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
            log.warn("Failed to emit SSE heartbeat", ex);
        }
    }

    private void sendHeartbeats() {
        emittersByUser.forEach((userId, userEmitters) ->
                userEmitters.forEach((sessionId, emitter) -> {
                    try {
                        emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
                    } catch (IOException | IllegalStateException ex) {
                        removeEmitter(userId, sessionId, emitter);
                        emitter.completeWithError(ex);
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
