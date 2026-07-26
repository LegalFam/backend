package com.legalfam.backend.auth.infrastructure.mail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
public class AuthMailTemplateRenderer {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Renders a {{placeholder}} template. Values are HTML-escaped for .html templates
     * so a name like {@code <script>} cannot inject markup into the message.
     */
    public String render(String templateName, Map<String, String> values) {
        String rendered = cache.computeIfAbsent(templateName, AuthMailTemplateRenderer::load);
        boolean escape = templateName.endsWith(".html");

        for (Map.Entry<String, String> value : values.entrySet()) {
            String replacement = value.getValue() == null ? "" : value.getValue();
            rendered = rendered.replace(
                    "{{" + value.getKey() + "}}",
                    escape ? escapeHtml(replacement) : replacement
            );
        }
        return rendered;
    }

    private static String load(String templateName) {
        ClassPathResource resource = new ClassPathResource("mail/" + templateName);
        try (InputStream input = resource.getInputStream()) {
            return StreamUtils.copyToString(input, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Mail template is unavailable: " + templateName, ex);
        }
    }

    private static String escapeHtml(String raw) {
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
