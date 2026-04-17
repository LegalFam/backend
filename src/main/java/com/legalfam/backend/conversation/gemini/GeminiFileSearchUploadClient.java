package com.legalfam.backend.conversation.gemini;

import com.legalfam.backend.conversation.dto.FileSearchUploadResponse;
import com.legalfam.backend.conversation.dto.FileSearchStoreResponse;
import com.legalfam.backend.conversation.exception.ConversationUpstreamException;
import com.legalfam.backend.error.exception.InvalidRequestException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class GeminiFileSearchUploadClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiFileSearchUploadClient.class);
    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String uploadStoreName;
    private final int pollAttempts;
    private final long pollDelayMs;

    public GeminiFileSearchUploadClient(
            ObjectMapper objectMapper,
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.upload-store-name:}") String uploadStoreName,
            @Value("${app.gemini.upload-poll-attempts:12}") int pollAttempts,
            @Value("${app.gemini.upload-poll-delay-ms:2000}") long pollDelayMs
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.uploadStoreName = normalizeStoreName(uploadStoreName);
        this.pollAttempts = Math.max(0, pollAttempts);
        this.pollDelayMs = Math.max(200, pollDelayMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public FileSearchUploadResponse uploadDocument(MultipartFile file, String displayName) {
        validateConfiguration();
        validateFile(file);

        String effectiveDisplayName = (displayName == null || displayName.isBlank())
                ? file.getOriginalFilename()
                : displayName.trim();
        if (effectiveDisplayName == null || effectiveDisplayName.isBlank()) {
            effectiveDisplayName = "uploaded-file";
        }

        try {
            log.info(
                    "Gemini upload started: store={}, fileName={}, sizeBytes={}, mimeType={}, displayName={}",
                    uploadStoreName,
                    file.getOriginalFilename(),
                    file.getSize(),
                    file.getContentType(),
                    effectiveDisplayName
            );
            JsonNode operation = startUpload(file, effectiveDisplayName);
            String operationName = readText(operation, "name");
            if (operationName.isBlank()) {
                log.error("Gemini upload failed: operation name missing in response");
                throw new ConversationUpstreamException("Gemini upload operation did not return a name");
            }

            JsonNode resolvedOperation = pollUntilDone(operationName, operation);
            boolean done = resolvedOperation.path("done").asBoolean(false);
            String documentName = readText(resolvedOperation.path("response"), "name");
            log.info(
                    "Gemini upload completed: operationName={}, done={}, documentName={}",
                    operationName,
                    done,
                    documentName
            );

            return new FileSearchUploadResponse(operationName, done, documentName);
        } catch (IOException e) {
            log.error("Gemini upload failed: io error", e);
            throw new ConversationUpstreamException("Gemini file upload service unavailable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Gemini upload interrupted", e);
            throw new ConversationUpstreamException("Gemini file upload request interrupted");
        }
    }

    public List<FileSearchStoreResponse> listStores() {
        validateApiKeyOnly();

        try {
            URI uri = URI.create(API_BASE_URL + "/v1beta/fileSearchStores?key=" + encode(apiKey));
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                String apiErrorMessage = extractApiErrorMessage(response.body());
                log.error(
                        "Gemini list fileSearchStores failed: status={}, apiErrorMessage={}, body={}",
                        response.statusCode(),
                        apiErrorMessage,
                        response.body()
                );
                throw new ConversationUpstreamException("Gemini list file search stores failed");
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode stores = root.path("fileSearchStores");
            if (!stores.isArray()) {
                return List.of();
            }

            List<FileSearchStoreResponse> result = new java.util.ArrayList<>();
            for (JsonNode store : stores) {
                result.add(new FileSearchStoreResponse(
                        readText(store, "name"),
                        readText(store, "displayName"),
                        pickText(store, "createTime", "create_time"),
                        pickText(store, "updateTime", "update_time")
                ));
            }

            log.info("Gemini list fileSearchStores success: count={}", result.size());
            return result;
        } catch (IOException e) {
            log.error("Gemini list fileSearchStores failed: io error", e);
            throw new ConversationUpstreamException("Gemini list file search stores service unavailable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Gemini list fileSearchStores interrupted", e);
            throw new ConversationUpstreamException("Gemini list file search stores request interrupted");
        }
    }

    private JsonNode startUpload(MultipartFile file, String displayName) throws IOException, InterruptedException {
        String boundary = "Boundary-" + System.currentTimeMillis();
        String mimeType = file.getContentType() == null || file.getContentType().isBlank()
                ? "application/octet-stream"
                : file.getContentType();

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("displayName", displayName);
        metadata.put("mimeType", mimeType);

        byte[] body = buildMultipartBody(boundary, objectMapper.writeValueAsString(metadata), mimeType, file.getBytes());
        URI uri = URI.create(
                API_BASE_URL
                        + "/upload/v1beta/"
                        + uploadStoreName
                        + ":uploadToFileSearchStore?uploadType=multipart&key="
                        + encode(apiKey)
        );

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "multipart/related; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            String apiErrorMessage = extractApiErrorMessage(response.body());
            log.error(
                    "Gemini uploadToFileSearchStore failed: status={}, apiErrorMessage={}, body={}",
                    response.statusCode(),
                    apiErrorMessage,
                    response.body()
            );
            throw new ConversationUpstreamException("Gemini upload to file search store failed");
        }
        log.info("Gemini uploadToFileSearchStore accepted: status={}", response.statusCode());
        return objectMapper.readTree(response.body());
    }

    private JsonNode pollUntilDone(String operationName, JsonNode initialOperation) throws IOException, InterruptedException {
        JsonNode current = initialOperation;
        if (current.path("done").asBoolean(false)) {
            String operationError = readText(current.path("error"), "message");
            if (!operationError.isBlank()) {
                log.error(
                        "Gemini operation completed with error: operationName={}, attempt=0, apiErrorMessage={}",
                        operationName,
                        operationError
                );
                throw new ConversationUpstreamException("Gemini upload operation failed");
            }
            return current;
        }

        for (int i = 0; i < pollAttempts; i++) {
            Thread.sleep(pollDelayMs);
            URI uri = URI.create(API_BASE_URL + "/v1beta/" + operationName + "?key=" + encode(apiKey));
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                String apiErrorMessage = extractApiErrorMessage(response.body());
                log.error(
                        "Gemini operation poll failed: operationName={}, attempt={}, status={}, apiErrorMessage={}, body={}",
                        operationName,
                        i + 1,
                        response.statusCode(),
                        apiErrorMessage,
                        response.body()
                );
                throw new ConversationUpstreamException("Gemini upload operation lookup failed");
            }

            current = objectMapper.readTree(response.body());
            String operationError = readText(current.path("error"), "message");
            if (!operationError.isBlank()) {
                log.error(
                        "Gemini operation completed with error: operationName={}, attempt={}, apiErrorMessage={}",
                        operationName,
                        i + 1,
                        operationError
                );
                throw new ConversationUpstreamException("Gemini upload operation failed");
            }
            log.info(
                    "Gemini operation poll: operationName={}, attempt={}, done={}",
                    operationName,
                    i + 1,
                    current.path("done").asBoolean(false)
            );
            if (current.path("done").asBoolean(false)) {
                return current;
            }
        }

        log.warn("Gemini operation poll timeout: operationName={}, attempts={}", operationName, pollAttempts);
        return current;
    }

    private byte[] buildMultipartBody(String boundary, String metadataJson, String mimeType, byte[] fileBytes) {
        byte[] prefix = (
                "--" + boundary + "\r\n"
                        + "Content-Type: application/json; charset=UTF-8\r\n\r\n"
                        + metadataJson + "\r\n"
                        + "--" + boundary + "\r\n"
                        + "Content-Type: " + mimeType + "\r\n\r\n"
        ).getBytes(StandardCharsets.UTF_8);
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

        byte[] body = new byte[prefix.length + fileBytes.length + suffix.length];
        System.arraycopy(prefix, 0, body, 0, prefix.length);
        System.arraycopy(fileBytes, 0, body, prefix.length, fileBytes.length);
        System.arraycopy(suffix, 0, body, prefix.length + fileBytes.length, suffix.length);
        return body;
    }

    private void validateConfiguration() {
        if (apiKey.isBlank()) {
            log.error("Gemini upload config error: api key missing");
            throw new ConversationUpstreamException("Gemini API key is not configured");
        }
        if (uploadStoreName.isBlank()) {
            log.error("Gemini upload config error: upload store missing");
            throw new ConversationUpstreamException("Gemini upload store name is not configured");
        }
    }

    private void validateApiKeyOnly() {
        if (apiKey.isBlank()) {
            log.error("Gemini config error: api key missing");
            throw new ConversationUpstreamException("Gemini API key is not configured");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            log.warn("Gemini upload validation failed: file missing");
            throw new InvalidRequestException("File is required");
        }
        if (file.getSize() > 100L * 1024 * 1024) {
            log.warn("Gemini upload validation failed: file too large sizeBytes={}", file.getSize());
            throw new InvalidRequestException("File exceeds 100MB upload limit");
        }
    }

    private String normalizeStoreName(String rawStoreName) {
        if (rawStoreName == null || rawStoreName.isBlank()) {
            return "";
        }
        String value = rawStoreName.trim();
        if (value.startsWith("fileSearchStores/")) {
            return value;
        }
        return "fileSearchStores/" + value;
    }

    private String readText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field == null || field.isNull() ? "" : field.asText("");
    }

    private String pickText(JsonNode node, String camelCase, String snakeCase) {
        String camel = readText(node, camelCase);
        if (!camel.isBlank()) {
            return camel;
        }
        return readText(node, snakeCase);
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
