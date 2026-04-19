package com.legalfam.backend.conversation.integration;

import com.legalfam.backend.conversation.exception.ConversationUpstreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.SocketTimeoutException;
import java.util.List;

@Component
public class N8nWebhookClient {

    private static final Logger log = LoggerFactory.getLogger(N8nWebhookClient.class);
    private static final int MIN_TIMEOUT_MS = 1000;
    private static final int MAX_LOG_BODY_SIZE = 400;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final String webhookUrl;
    private final String authHeaderName;
    private final String authToken;
    private final int timeoutMs;

    public N8nWebhookClient(
            ObjectMapper objectMapper,
            @Value("${app.n8n.webhook-url:}") String webhookUrl,
            @Value("${app.n8n.auth-header-name:X-N8N-Token}") String authHeaderName,
            @Value("${app.n8n.auth-token:}") String authToken,
            @Value("${app.n8n.timeout-ms:30000}") int timeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.webhookUrl = webhookUrl;
        this.authHeaderName = authHeaderName;
        this.authToken = authToken;
        this.timeoutMs = Math.max(timeoutMs, MIN_TIMEOUT_MS);
        this.restTemplate = buildRestTemplate(this.timeoutMs);
    }

    public JsonNode sendPrompt(String prompt) {
        log.info("Preparing n8n webhook call: configuredUrl={}, timeoutMs={}",
                webhookUrl == null || webhookUrl.isBlank() ? "<empty>" : webhookUrl,
                timeoutMs);

        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("Skipping n8n call because app.n8n.webhook-url is empty");
            throw new ConversationUpstreamException("n8n webhook URL is not configured");
        }

        validateWebhookUrl(webhookUrl);
        String payloadJson = buildPayload(prompt);
        HttpEntity<String> requestEntity = buildRequestEntity(payloadJson);
        ResponseEntity<String> response;

        try {
            log.info("Calling n8n webhook: url={}, promptLength={}", webhookUrl, prompt.length());
            response = restTemplate.exchange(webhookUrl.trim(), HttpMethod.POST, requestEntity, String.class);
        } catch (HttpStatusCodeException ex) {
            ex.getResponseBodyAsString();
            String bodyPreview = ex.getResponseBodyAsString().trim();
            if (bodyPreview.length() > MAX_LOG_BODY_SIZE) {
                bodyPreview = bodyPreview.substring(0, MAX_LOG_BODY_SIZE);
            }
            int statusCode = ex.getStatusCode().value();
            log.warn("n8n webhook returned non-success status: status={}, body={}", statusCode, bodyPreview);
            throw new ConversationUpstreamException("n8n webhook returned status " + statusCode);
        } catch (ResourceAccessException ex) {
            if (isTimeout(ex)) {
                throw new ConversationUpstreamException("n8n service timeout");
            }
            log.error("Failed to reach n8n webhook", ex);
            throw new ConversationUpstreamException("n8n service unavailable");
        } catch (RuntimeException ex) {
            log.error("Unexpected error while calling n8n webhook", ex);
            throw new ConversationUpstreamException("n8n service unavailable");
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            String bodyPreview = response.getBody() == null ? "" : response.getBody().trim();
            if (bodyPreview.length() > MAX_LOG_BODY_SIZE) {
                bodyPreview = bodyPreview.substring(0, MAX_LOG_BODY_SIZE);
            }
            int statusCode = response.getStatusCode().value();
            log.warn("n8n webhook returned non-success status: status={}, body={}", statusCode, bodyPreview);
            throw new ConversationUpstreamException("n8n webhook returned status " + statusCode);
        }

        return parseResponseBody(response.getBody());
    }

    private void validateWebhookUrl(String url) {
        try {
            URI.create(url.trim());
        } catch (IllegalArgumentException ex) {
            throw new ConversationUpstreamException("n8n webhook URL is invalid");
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

    private String buildPayload(String prompt) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("prompt", prompt);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new ConversationUpstreamException("Failed to serialize n8n request");
        }
    }

    private JsonNode parseResponseBody(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new ConversationUpstreamException("n8n returned an empty response");
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
