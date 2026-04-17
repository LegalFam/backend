package com.legalfam.backend.conversation.gemini;

import jakarta.annotation.PostConstruct;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GeminiStoreConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(GeminiStoreConfigValidator.class);

    private final String askStoreName;
    private final String uploadStoreName;
    private final String askStoreNamesCsv;

    public GeminiStoreConfigValidator(
            @Value("${app.gemini.file-search-store-name:}") String askStoreName,
            @Value("${app.gemini.upload-store-name:${app.gemini.file-search-store-name:}}") String uploadStoreName,
            @Value("${app.gemini.file-search-store-names:}") String askStoreNamesCsv
    ) {
        this.askStoreName = normalizeStoreName(askStoreName);
        this.uploadStoreName = normalizeStoreName(uploadStoreName);
        this.askStoreNamesCsv = askStoreNamesCsv == null ? "" : askStoreNamesCsv;
    }

    @PostConstruct
    void validate() {
        List<String> csvStores = parseStoreNames(askStoreNamesCsv);
        if (csvStores.size() > 1) {
            throw new IllegalStateException(
                    "Only one Gemini file search store is supported for now. "
                            + "Set a single store in GEMINI_FILE_SEARCH_STORE_NAME."
            );
        }

        String csvStore = csvStores.isEmpty() ? "" : csvStores.get(0);
        String resolvedAskStore = !askStoreName.isBlank() ? askStoreName : csvStore;
        String resolvedUploadStore = uploadStoreName;

        if (resolvedAskStore.isBlank()) {
            throw new IllegalStateException("Gemini file search store is not configured");
        }
        if (resolvedUploadStore.isBlank()) {
            throw new IllegalStateException("Gemini upload store is not configured");
        }
        if (!resolvedAskStore.equals(resolvedUploadStore)) {
            throw new IllegalStateException(
                    "Gemini ask store and upload store must be the same. "
                            + "ask=" + resolvedAskStore + ", upload=" + resolvedUploadStore
            );
        }

        log.info("Gemini store config validated: store={}", resolvedAskStore);
    }

    private List<String> parseStoreNames(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(this::normalizeStoreName)
                .distinct()
                .toList();
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
}
