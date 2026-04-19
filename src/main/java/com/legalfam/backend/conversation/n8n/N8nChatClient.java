package com.legalfam.backend.conversation.n8n;

import com.legalfam.backend.conversation.dto.ConversationAskResponse;
import com.legalfam.backend.conversation.dto.ConversationCitationResponse;
import com.legalfam.backend.conversation.exception.ConversationUpstreamException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class N8nChatClient {

    private static final Logger log = LoggerFactory.getLogger(N8nChatClient.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String webhookUrl;
    private final String authHeaderName;
    private final String authToken;
    private final Duration requestTimeout;

    public N8nChatClient(
            ObjectMapper objectMapper,
            @Value("${app.n8n.webhook-url:}") String webhookUrl,
            @Value("${app.n8n.auth-header-name:X-N8N-Token}") String authHeaderName,
            @Value("${app.n8n.auth-token:}") String authToken,
            @Value("${app.n8n.timeout-ms:30000}") long timeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.authHeaderName = authHeaderName == null || authHeaderName.isBlank()
                ? "X-N8N-Token"
                : authHeaderName.trim();
        this.authToken = authToken == null ? "" : authToken.trim();
        this.requestTimeout = Duration.ofMillis(Math.max(1000, timeoutMs));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public ConversationAskResponse generateAnswer(String prompt) {
        if (webhookUrl.isBlank()) {
            log.error("n8n config error: webhook url missing");
            throw new ConversationUpstreamException("n8n webhook URL is not configured");
        }

        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("prompt", prompt);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json");

            if (!authToken.isBlank()) {
                requestBuilder.header(authHeaderName, authToken);
            }

            HttpRequest request = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.error("n8n webhook failed: status={}, body={}", response.statusCode(), response.body());
                throw new ConversationUpstreamException("n8n chat request failed");
            }

            return parseResponse(response.body());
        } catch (IOException e) {
            log.error("n8n chat failed: io error", e);
            throw new ConversationUpstreamException("n8n chat service unavailable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("n8n chat interrupted", e);
            throw new ConversationUpstreamException("n8n chat request interrupted");
        }
    }

    private ConversationAskResponse parseResponse(String body) {
        if (body == null || body.isBlank()) {
            throw new ConversationUpstreamException("n8n returned an empty response");
        }

        JsonNode root = objectMapper.readTree(body);
        String answer = firstNonBlank(
                readText(root, "answer"),
                readText(root, "reply"),
                readText(root, "response"),
                readText(root, "output")
        );

        List<ConversationCitationResponse> citations = extractCitations(root);
        if (answer.isBlank()) {
            throw new ConversationUpstreamException("n8n returned an invalid response");
        }

        return new ConversationAskResponse(answer, citations);
    }

    private List<ConversationCitationResponse> extractCitations(JsonNode root) {
        JsonNode citationsNode = root.path("citations");
        if (!citationsNode.isArray()) {
            citationsNode = root.path("sources");
        }

        if (!citationsNode.isArray()) {
            return List.of();
        }

        Map<String, ConversationCitationResponse> unique = new LinkedHashMap<>();
        for (JsonNode citation : citationsNode) {
            String fileId = firstNonBlank(
                    readText(citation, "fileId"),
                    readText(citation, "sourceId"),
                    readText(citation, "id"),
                    readText(citation, "uri")
            );
            String fileName = firstNonBlank(
                    readText(citation, "fileName"),
                    readText(citation, "sourceName"),
                    readText(citation, "name"),
                    readText(citation, "title")
            );
            String snippet = firstNonBlank(
                    readText(citation, "snippet"),
                    readText(citation, "text"),
                    readText(citation, "excerpt"),
                    readText(citation, "content")
            );

            if (fileId.isBlank() && fileName.isBlank() && snippet.isBlank()) {
                continue;
            }

            String key = fileId + "|" + fileName + "|" + snippet;
            unique.putIfAbsent(key, new ConversationCitationResponse(fileId, fileName, snippet));
        }

        return new ArrayList<>(unique.values());
    }

    private String readText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field == null || field.isNull() ? "" : field.asString("");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}

