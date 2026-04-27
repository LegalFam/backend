package com.legalfam.backend.chat.infrastructure.integration;

import com.legalfam.backend.chat.application.dto.ChatCitationResponse;
import com.legalfam.backend.chat.application.event.ChatMessageQueuedEvent;
import com.legalfam.backend.chat.application.service.ChatAssistantPersistenceService;
import com.legalfam.backend.chat.application.service.ChatAssistantPersistenceService.ChatAssistantErrorDispatch;
import com.legalfam.backend.chat.application.service.ChatAssistantPersistenceService.ChatAssistantMessageDispatch;
import com.legalfam.backend.chat.infrastructure.sse.ChatSseEmitterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ChatAsyncProcessor {

    private static final Logger log = LoggerFactory.getLogger(ChatAsyncProcessor.class);

    private final N8nWebhookClient n8nWebhookClient;
    private final ChatAssistantPersistenceService chatAssistantPersistenceService;
    private final ChatSseEmitterService chatSseEmitterService;

    public ChatAsyncProcessor(
            N8nWebhookClient n8nWebhookClient,
            ChatAssistantPersistenceService chatAssistantPersistenceService,
            ChatSseEmitterService chatSseEmitterService
    ) {
        this.n8nWebhookClient = n8nWebhookClient;
        this.chatAssistantPersistenceService = chatAssistantPersistenceService;
        this.chatSseEmitterService = chatSseEmitterService;
    }

    @Async("chatTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void process(ChatMessageQueuedEvent event) {
        UUID chatSessionId = event.chatSessionId();
        String userMessageInput = event.userMessageInput();

        JsonNode root;
        try {
            root = n8nWebhookClient.sendMessage(userMessageInput, chatSessionId);
        } catch (RuntimeException ex) {
            log.warn("n8n webhook call failed for chatSessionId={}: {}", chatSessionId, ex.getMessage());
            persistAndDispatchFailure(
                    chatSessionId,
                    resolveErrorCode(ex.getMessage()),
                    buildFailureMessage(ex.getMessage())
            );
            return;
        }

        String message = readText(root, "message");
        if (isBlank(message)) {
            log.warn("n8n response has empty message for chatSessionId={}", chatSessionId);
            persistAndDispatchFailure(
                    chatSessionId,
                    "UPSTREAM_EMPTY_RESPONSE",
                    "Assistant unavailable. Empty response received from upstream service."
            );
            return;
        }

        List<ChatCitationResponse> citations = extractCitations(root.get("citations"));
        ChatAssistantMessageDispatch dispatch = chatAssistantPersistenceService.persistAssistantMessage(
                chatSessionId,
                message,
                citations
        );
        if (dispatch == null) {
            return;
        }

        chatSseEmitterService.dispatchAssistantMessage(dispatch.userId(), dispatch.chatSessionId(), dispatch.event());
    }

    private void persistAndDispatchFailure(UUID chatSessionId, String errorCode, String errorMessage) {
        ChatAssistantErrorDispatch dispatch = chatAssistantPersistenceService.persistAssistantFailure(
                chatSessionId,
                errorCode,
                errorMessage
        );
        if (dispatch == null) {
            return;
        }
        chatSseEmitterService.dispatchAssistantError(dispatch.userId(), dispatch.chatSessionId(), dispatch.event());
    }

    private List<ChatCitationResponse> extractCitations(JsonNode citationsNode) {
        if (citationsNode == null || citationsNode.isNull()) {
            return List.of();
        }
        if (citationsNode.isArray()) {
            List<ChatCitationResponse> citations = new ArrayList<>();
            for (JsonNode citationNode : citationsNode) {
                ChatCitationResponse citation = mapCitation(citationNode);
                if (citation != null) {
                    citations.add(citation);
                }
            }
            return citations;
        }
        if (citationsNode.isObject()) {
            ChatCitationResponse singleCitation = mapCitation(citationsNode);
            if (singleCitation != null) {
                return List.of(singleCitation);
            }
        }
        return List.of();
    }

    private ChatCitationResponse mapCitation(JsonNode citationNode) {
        if (citationNode == null || citationNode.isNull()) {
            return null;
        }

        String sourceTitle = readText(citationNode, "file_name");
        String sourceSnippet = readText(citationNode, "snippet");
        String sourceUrl = readText(citationNode, "file_url");

        if (isBlank(sourceTitle) && isBlank(sourceSnippet) && isBlank(sourceUrl)) {
            return null;
        }
        if (isBlank(sourceUrl)) {
            return null;
        }
        return new ChatCitationResponse(sourceTitle, sourceSnippet, sourceUrl);
    }

    private String readText(JsonNode node, String key) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode child = node.get(key);
        if (child == null || child.isNull()) {
            return null;
        }
        String text = child.isTextual() ? child.asText() : child.toString();
        return isBlank(text) ? null : text.trim();
    }

    private String buildFailureMessage(String upstreamMessage) {
        if (isBlank(upstreamMessage)) {
            return "Assistant unavailable. Upstream service failed.";
        }
        return "Assistant unavailable. " + upstreamMessage.trim();
    }

    private String resolveErrorCode(String upstreamMessage) {
        if (isBlank(upstreamMessage)) {
            return "UPSTREAM_ERROR";
        }
        if (upstreamMessage.contains("status 404")) {
            return "UPSTREAM_404";
        }
        if (upstreamMessage.contains("status 5")) {
            return "UPSTREAM_5XX";
        }
        if (upstreamMessage.contains("timeout")) {
            return "UPSTREAM_TIMEOUT";
        }
        return "UPSTREAM_ERROR";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
