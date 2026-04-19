package com.legalfam.backend.conversation.gemini;

import com.legalfam.backend.conversation.dto.ConversationAskResponse;
import com.legalfam.backend.conversation.dto.ConversationCitationResponse;
import com.legalfam.backend.conversation.exception.ConversationUpstreamException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class GeminiFileSearchClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiFileSearchClient.class);
    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;

    public GeminiFileSearchClient(
            ObjectMapper objectMapper,
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.model:gemini-2.5-flash}") String model
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "gemini-2.5-flash" : model.trim();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public ConversationAskResponse generateAnswer(String prompt, String fileSearchStoreName) {
        String normalizedStoreName = normalizeStoreName(fileSearchStoreName);
        validateConfiguration(normalizedStoreName);
        log.info(
                "Gemini file search ask started: model={}, store={}, promptLength={}",
                model,
                normalizedStoreName,
                prompt.length()
        );

        try {
            String responseBody = callGenerateContent(prompt, normalizedStoreName);
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidate = root.path("candidates").path(0);

            String answer = extractAnswer(candidate);
            List<ConversationCitationResponse> citations = extractCitations(candidate);
            log.info("Gemini file search ask success: answerLength={}, citations={}", answer.length(), citations.size());

            return new ConversationAskResponse(answer, citations);
        } catch (IOException e) {
            log.error("Gemini file search ask failed: io error", e);
            throw new ConversationUpstreamException("Gemini file search service unavailable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Gemini file search ask interrupted", e);
            throw new ConversationUpstreamException("Gemini file search request interrupted");
        }
    }

    private void validateConfiguration(String fileSearchStoreName) {
        if (apiKey.isBlank()) {
            log.error("Gemini file search config error: api key missing");
            throw new ConversationUpstreamException("Gemini API key is not configured");
        }
        if (fileSearchStoreName.isBlank()) {
            throw new ConversationUpstreamException("Gemini file search store name is required");
        }
    }

    private String callGenerateContent(String prompt, String fileSearchStoreName) throws IOException, InterruptedException {
        ObjectNode root = objectMapper.createObjectNode();

        ArrayNode contents = root.putArray("contents");
        ObjectNode userContent = contents.addObject();
        ArrayNode parts = userContent.putArray("parts");
        parts.addObject().put("text", prompt);

        ArrayNode tools = root.putArray("tools");
        ObjectNode tool = tools.addObject();
        ObjectNode fileSearch = tool.putObject("file_search");
        ArrayNode storeNames = fileSearch.putArray("file_search_store_names");
        storeNames.add(fileSearchStoreName);

        URI uri = URI.create(
                API_BASE_URL
                        + "/models/"
                        + encode(model)
                        + ":generateContent?key="
                        + encode(apiKey)
        );

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(root)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            String apiErrorMessage = extractApiErrorMessage(response.body());
            log.error(
                    "Gemini generateContent failed: status={}, apiErrorMessage={}, body={}",
                    response.statusCode(),
                    apiErrorMessage,
                    response.body()
            );
            throw new ConversationUpstreamException("Gemini generate content request failed");
        }

        return response.body();
    }

    private String extractAnswer(JsonNode candidate) {
        JsonNode content = candidate.path("content");
        JsonNode parts = content.path("parts");
        if (!parts.isArray()) {
            throw new ConversationUpstreamException("Gemini returned an invalid response");
        }

        StringBuilder answer = new StringBuilder();
        for (JsonNode part : parts) {
            String text = readText(part, "text");
            if (!text.isBlank()) {
                if (!answer.isEmpty()) {
                    answer.append('\n');
                }
                answer.append(text);
            }
        }

        if (answer.isEmpty()) {
            throw new ConversationUpstreamException("Gemini returned an empty response");
        }

        return answer.toString();
    }

    private List<ConversationCitationResponse> extractCitations(JsonNode candidate) {
        Map<String, ConversationCitationResponse> unique = new LinkedHashMap<>();
        JsonNode groundingMetadata = pick(candidate, "groundingMetadata", "grounding_metadata");
        JsonNode groundingChunks = pick(groundingMetadata, "groundingChunks", "grounding_chunks");

        if (groundingChunks.isArray()) {
            for (JsonNode chunk : groundingChunks) {
                JsonNode retrievedContext = pick(chunk, "retrievedContext", "retrieved_context");
                if (!retrievedContext.isObject()) {
                    continue;
                }

                String uri = readText(retrievedContext, "uri");
                String title = readText(retrievedContext, "title");
                String snippet = readText(retrievedContext, "text");

                String sourceId = !uri.isBlank() ? uri : title;
                addCitation(unique, sourceId, title, snippet);
            }
        }

        return new ArrayList<>(unique.values());
    }

    private JsonNode pick(JsonNode node, String camelCase, String snakeCase) {
        JsonNode camel = node.path(camelCase);
        if (!camel.isMissingNode() && !camel.isNull()) {
            return camel;
        }
        return node.path(snakeCase);
    }

    private void addCitation(
            Map<String, ConversationCitationResponse> unique,
            String sourceId,
            String sourceName,
            String snippet
    ) {
        if (sourceId.isBlank() && sourceName.isBlank() && snippet.isBlank()) {
            return;
        }
        String key = sourceId + "|" + sourceName + "|" + snippet;
        unique.putIfAbsent(key, new ConversationCitationResponse(sourceId, sourceName, snippet));
    }

    private String normalizeStoreName(String rawStoreName) {
        if (rawStoreName == null || rawStoreName.isBlank()) {
            return "";
        }
        rawStoreName = rawStoreName.trim();
        if (rawStoreName.startsWith("fileSearchStores/")) {
            return rawStoreName;
        }
        return "fileSearchStores/" + rawStoreName;
    }

    private String readText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field == null || field.isNull() ? "" : field.asText("");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String extractApiErrorMessage(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String nested = readText(root.path("error"), "message");
            if (!nested.isBlank()) {
                return nested;
            }

            String direct = readText(root, "message");
            if (!direct.isBlank()) {
                return direct;
            }
        } catch (Exception ignored) {
            // Preserve log stability if response is not JSON.
        }
        return "unknown";
    }
}
