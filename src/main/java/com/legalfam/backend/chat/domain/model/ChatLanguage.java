package com.legalfam.backend.chat.domain.model;

import java.util.Locale;

/**
 * Idiomas en los que se puede leer la orientacion legal.
 *
 * <p>El pipeline juridico (clasificacion, RAG, redaccion, corpus y citas) opera siempre en
 * espanol: la traduccion ocurre solo en los bordes. Este enum no elige el idioma del
 * razonamiento, solo el de la lectura, y es la unica lista blanca del sistema — la
 * validacion del request, la persistencia y el payload hacia n8n se apoyan en el.
 */
public enum ChatLanguage {

    /** Espanol. Es el idioma canonico: lo que se guarda en {@code chat_message.content}. */
    ES("es"),
    /** Quechua sureno (Chanka-Collao). No se distinguen las variantes centrales. */
    QU("qu"),
    /** Aymara. */
    AY("ay");

    private final String code;

    ChatLanguage(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public boolean isSpanish() {
        return this == ES;
    }

    /**
     * Un idioma ausente, en blanco o desconocido cae a espanol. Nunca lanza: el borde que
     * valida la entrada del usuario es {@code ChatAskRequest}; aca solo se normaliza para
     * que un evento antiguo en la cola o una fila previa a la migracion sigan siendo
     * legibles.
     */
    public static ChatLanguage fromCode(String value) {
        if (value == null || value.isBlank()) {
            return ES;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ChatLanguage language : values()) {
            if (language.code.equals(normalized)) {
                return language;
            }
        }
        return ES;
    }
}
