package com.legalfam.backend.auth.infrastructure.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(String allowedOrigins) {

    public List<String> allowedOriginPatterns() {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            return List.of("*");
        }
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        return origins.isEmpty() ? List.of("*") : origins;
    }
}
