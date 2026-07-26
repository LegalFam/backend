package com.legalfam.backend.auth.infrastructure.mail;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthMailTemplateRendererTest {

    private AuthMailTemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new AuthMailTemplateRenderer();
    }

    @Test
    void replacesEveryPlaceholder() {
        String rendered = renderer.render("verify-email.txt", values("Juan"));

        assertTrue(rendered.contains("Juan"));
        assertTrue(rendered.contains("https://legalfam.pe/verificar-correo?token=abc"));
        assertTrue(rendered.contains("1440"));
        assertFalse(rendered.contains("{{"), "no placeholder should survive: " + rendered);
    }

    @Test
    void escapesHtmlInTheHtmlTemplate() {
        String rendered = renderer.render("verify-email.html", values("<script>alert(1)</script>"));

        assertFalse(rendered.contains("<script>"));
        assertTrue(rendered.contains("&lt;script&gt;"));
    }

    @Test
    void doesNotEscapeThePlainTextTemplate() {
        String rendered = renderer.render("verify-email.txt", values("Juan & Maria"));

        assertTrue(rendered.contains("Juan & Maria"));
    }

    @Test
    void throwsWhenTheTemplateDoesNotExist() {
        assertThrows(
                IllegalStateException.class,
                () -> renderer.render("does-not-exist.txt", values("Juan"))
        );
    }

    private static Map<String, String> values(String name) {
        return Map.of(
                "name", name,
                "actionUrl", "https://legalfam.pe/verificar-correo?token=abc",
                "expiresInMinutes", "1440",
                "appName", "LegalFam"
        );
    }
}
