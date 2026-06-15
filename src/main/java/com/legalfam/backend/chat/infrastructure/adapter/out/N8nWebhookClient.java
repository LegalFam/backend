package com.legalfam.backend.chat.infrastructure.adapter.out;

import com.legalfam.backend.chat.application.dto.ChatAssistantGatewayResponse;
import com.legalfam.backend.chat.application.dto.ChatAssistantMetadata;
import com.legalfam.backend.chat.application.dto.ChatCitationResponse;
import com.legalfam.backend.chat.application.dto.ChatPreviousMessage;
import com.legalfam.backend.chat.application.port.out.IChatAssistantGatewayPort;
import com.legalfam.backend.chat.domain.exception.ChatUpstreamException;
import com.legalfam.backend.chat.infrastructure.config.N8nProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class N8nWebhookClient implements IChatAssistantGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(N8nWebhookClient.class);
    private static final int MIN_TIMEOUT_MS = 1000;
    private static final int MAX_LOG_BODY_SIZE = 400;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final String webhookUrl;
    private final String authHeaderName;
    private final String authToken;
    private final int timeoutMs;

    public N8nWebhookClient(ObjectMapper objectMapper, N8nProperties properties) {
        this.objectMapper = objectMapper;
        this.webhookUrl = properties.normalizedWebhookUrl();
        this.authHeaderName = properties.normalizedAuthHeaderName();
        this.authToken = properties.normalizedAuthToken();
        this.timeoutMs = Math.max(properties.safeTimeoutMs(), MIN_TIMEOUT_MS);
        this.restTemplate = buildRestTemplate(this.timeoutMs);
    }

    @Override
    public ChatAssistantGatewayResponse sendMessage(
            String message,
            UUID sessionId,
            List<ChatPreviousMessage> previousMessages
    ) {
        log.info("Preparing n8n webhook call: configuredUrl={}, timeoutMs={}",
                webhookUrl == null || webhookUrl.isBlank() ? "<empty>" : webhookUrl,
                timeoutMs);

        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("Skipping n8n call because app.n8n.webhook-url is empty");
            throw new ChatUpstreamException("UPSTREAM_NOT_CONFIGURED", "El servicio de respuesta no esta configurado.");
        }

        validateWebhookUrl(webhookUrl);
        String payloadJson = buildPayload(message, sessionId, previousMessages);
        HttpEntity<String> requestEntity = buildRequestEntity(payloadJson);
        ResponseEntity<String> response;

        try {
            log.info("Calling n8n webhook: url={}, messageLength={}, sessionId={}",
                    webhookUrl, message.length(), sessionId);
            response = restTemplate.exchange(webhookUrl.trim(), HttpMethod.POST, requestEntity, String.class);
        } catch (HttpStatusCodeException ex) {
            ex.getResponseBodyAsString();
            String bodyPreview = ex.getResponseBodyAsString().trim();
            if (bodyPreview.length() > MAX_LOG_BODY_SIZE) {
                bodyPreview = bodyPreview.substring(0, MAX_LOG_BODY_SIZE);
            }
            int statusCode = ex.getStatusCode().value();
            log.warn("n8n webhook returned non-success status: status={}, body={}", statusCode, bodyPreview);
            throw buildStatusException(statusCode, ex.getResponseBodyAsString());
        } catch (ResourceAccessException ex) {
            if (isTimeout(ex)) {
                throw new ChatUpstreamException("UPSTREAM_TIMEOUT", "La respuesta esta tardando mas de lo esperado.");
            }
            log.error("Failed to reach n8n webhook", ex);
            throw new ChatUpstreamException("UPSTREAM_UNAVAILABLE", "No se pudo conectar con el servicio de respuesta.");
        } catch (RuntimeException ex) {
            log.error("Unexpected error while calling n8n webhook", ex);
            throw new ChatUpstreamException("UPSTREAM_UNAVAILABLE", "El servicio de respuesta no esta disponible.");
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            String bodyPreview = response.getBody() == null ? "" : response.getBody().trim();
            if (bodyPreview.length() > MAX_LOG_BODY_SIZE) {
                bodyPreview = bodyPreview.substring(0, MAX_LOG_BODY_SIZE);
            }
            int statusCode = response.getStatusCode().value();
            log.warn("n8n webhook returned non-success status: status={}, body={}", statusCode, bodyPreview);
            throw buildStatusException(statusCode, response.getBody());
        }

        return mapResponse(parseResponseBody(response.getBody()));
    }

    private void validateWebhookUrl(String url) {
        try {
            URI.create(url.trim());
        } catch (IllegalArgumentException ex) {
            throw new ChatUpstreamException("UPSTREAM_NOT_CONFIGURED", "El servicio de respuesta esta mal configurado.");
        }
    }

    private HttpEntity<String> buildRequestEntity(String payloadJson) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        if (authToken != null && !authToken.isBlank() && authHeaderName != null && !authHeaderName.isBlank()) {
            headers.set(authHeaderName, authToken);
        }

        return new HttpEntity<>(payloadJson, headers);
    }

    private String buildPayload(String message, UUID sessionId, List<ChatPreviousMessage> previousMessages) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("message", message);
        payload.put("session_id", sessionId.toString());
        ArrayNode history = payload.putArray("previous_messages");
        for (ChatPreviousMessage previousMessage : previousMessages == null ? List.<ChatPreviousMessage>of() : previousMessages) {
            ObjectNode item = history.addObject();
            item.put("role", previousMessage.role());
            item.put("content", previousMessage.content());
            if (previousMessage.createdAt() != null) {
                item.put("created_at", previousMessage.createdAt().toString());
            }
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new ChatUpstreamException("UPSTREAM_REQUEST_INVALID", "No se pudo preparar la consulta para el servicio de respuesta.");
        }
    }

    private JsonNode parseResponseBody(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new ChatUpstreamException("UPSTREAM_EMPTY_RESPONSE", "No se recibio una respuesta valida.");
        }

        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception ex) {
            // Some n8n workflows return plain text instead of JSON.
            ObjectNode fallback = objectMapper.createObjectNode();
            fallback.put("message", responseBody.trim());
            return fallback;
        }
    }

    private ChatAssistantGatewayResponse mapResponse(JsonNode root) {
        return new ChatAssistantGatewayResponse(
                readText(root, "message"),
                extractCitations(root.get("citations")),
                extractMetadata(root)
        );
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

    private ChatAssistantMetadata extractMetadata(JsonNode root) {
        String citationSupportStatus = root != null && root.get("citationSupportStatus") != null
                ? readCitationSupportStatus(root)
                : inferCitationSupportStatus(root == null ? null : root.get("citations"));
        return new ChatAssistantMetadata(
                readText(root, "confidenceStatus"),
                readText(root, "confidenceReason"),
                readStringArray(root.get("nextSteps")),
                readBoolean(root, "specialistSupportRecommended"),
                citationSupportStatus
        );
    }

    private String readCitationSupportStatus(JsonNode root) {
        String value = readText(root, "citationSupportStatus");
        if (value == null) {
            return null;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "GOOD", "WEAK", "NONE" -> normalized;
            default -> null;
        };
    }

    private String inferCitationSupportStatus(JsonNode citationsNode) {
        return extractCitations(citationsNode).isEmpty() ? "NONE" : "GOOD";
    }

    private List<String> readStringArray(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.isTextual() ? item.asText() : item.toString();
            if (value != null && !value.isBlank()) {
                values.add(value.trim());
            }
        }
        return values;
    }

    private Boolean readBoolean(JsonNode node, String key) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode child = node.get(key);
        if (child == null || child.isNull()) {
            return null;
        }
        if (child.isBoolean()) {
            return child.asBoolean();
        }
        String text = child.isTextual() ? child.asText() : child.toString();
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        return null;
    }

    private ChatCitationResponse mapCitation(JsonNode citationNode) {
        if (citationNode == null || citationNode.isNull()) {
            return null;
        }

        String sourceTitle = readText(citationNode, "file_name");
        String sourceSnippet = readText(citationNode, "snippet");
        String sourceUrl = readText(citationNode, "file_url");

        if ((sourceTitle == null || sourceTitle.isBlank())
                && (sourceSnippet == null || sourceSnippet.isBlank())
                && (sourceUrl == null || sourceUrl.isBlank())) {
            return null;
        }
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return null;
        }
        return new ChatCitationResponse(sourceTitle, sourceSnippet, sourceUrl);
    }

    private ChatUpstreamException buildStatusException(int statusCode, String responseBody) {
        UpstreamError upstreamError = parseUpstreamError(responseBody);
        if (upstreamError != null) {
            return new ChatUpstreamException(upstreamError.code(), upstreamError.message());
        }
        if (statusCode == 408 || statusCode == 504) {
            return new ChatUpstreamException("UPSTREAM_TIMEOUT", "La respuesta esta tardando mas de lo esperado.");
        }
        if (statusCode >= 500) {
            return new ChatUpstreamException("UPSTREAM_UNAVAILABLE", "El servicio de respuesta no esta disponible.");
        }
        if (statusCode == 422) {
            return new ChatUpstreamException("AGENT_VALIDATION_FAILED", "No se pudo validar la respuesta generada.");
        }
        return new ChatUpstreamException("UPSTREAM_ERROR", "No se pudo completar la consulta.");
    }

    private UpstreamError parseUpstreamError(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode detail = root.get("detail");
            JsonNode source = detail != null && detail.isObject() ? detail : root;
            String code = readText(source, "code");
            String message = readText(source, "message");
            if (message == null) {
                message = readText(source, "detail");
            }
            if (code == null && message == null) {
                return null;
            }
            return new UpstreamError(
                    code == null ? "UPSTREAM_ERROR" : code,
                    message == null ? "No se pudo completar la consulta." : message
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private String readText(JsonNode node, String key) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode child = node.get(key);
        if (child == null || child.isNull()) {
            return null;
        }
        String value = child.isTextual() ? child.asText() : child.toString();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record UpstreamError(String code, String message) {
    }

    private RestTemplate buildRestTemplate(int timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        return new RestTemplate(requestFactory);
    }

    private boolean isTimeout(ResourceAccessException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof SocketTimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
