package com.legalfam.backend.chat.infrastructure.adapter.out;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Separa, del texto que transmite el XAI Agent, la parte que se le puede mostrar al usuario.
 *
 * <p>El agente escribe primero la respuesta en Markdown, luego {@link #DELIMITER} y al final
 * un JSON con citas y metadatos que no se muestra. El modelo no siempre copia el separador
 * tal cual (en pruebas reales escribio {@code ***LEGALFAM_META***}), asi que se reconoce por
 * la palabra clave con cualquier decoracion alrededor, y si ni siquiera aparece, por el
 * comienzo del JSON final. Es la misma regla que aplica el nodo {@code Parse XAI Output}.
 *
 * <p>Mientras llega, la cola del texto podria ser el comienzo de un separador partido entre
 * dos chunks; esa cola se retiene hasta saber si lo es, para que el usuario nunca lo vea.
 */
final class N8nAnswerStreamFilter {

    static final String DELIMITER = "<<<LEGALFAM_META>>>";

    private static final Pattern META_START = Pattern.compile(
            "[<>*_#`~\\[\\]\\t ]*LEGALFAM_META"
                    + "|\\n[ \\t]*(?:```(?:json)?[ \\t]*\\n[ \\t]*)?\\{\\s*\"citations\""
    );
    /** Cuanto hacia atras puede empezar un separador todavia incompleto. */
    private static final int PENDING_WINDOW = 64;

    private final StringBuilder raw = new StringBuilder();
    private boolean closed;
    private String lastVisible = "";

    /** Descarta lo acumulado: el flujo reintento la redaccion desde cero. */
    void reset() {
        raw.setLength(0);
        closed = false;
        lastVisible = "";
    }

    /** @return el nuevo texto visible completo, o {@code null} si no cambio. */
    String append(String chunk) {
        if (closed || chunk == null || chunk.isEmpty()) {
            return null;
        }
        raw.append(chunk);
        String visible = visible();
        if (visible.equals(lastVisible)) {
            return null;
        }
        lastVisible = visible;
        return visible;
    }

    String lastVisible() {
        return lastVisible;
    }

    /**
     * Sin espacios al final: el salto de linea que precede al separador apareceria y
     * desapareceria en pantalla, y en Markdown no cambia nada.
     */
    private String visible() {
        String text = raw.toString();
        Matcher matcher = META_START.matcher(text);
        if (matcher.find()) {
            closed = true;
            return text.substring(0, matcher.start()).stripTrailing();
        }
        return text.substring(0, pendingStart(text, matcher)).stripTrailing();
    }

    /** Primera posicion desde la que el resto del texto todavia podria volverse separador. */
    private static int pendingStart(String text, Matcher matcher) {
        for (int start = Math.max(0, text.length() - PENDING_WINDOW); start < text.length(); start++) {
            matcher.region(start, text.length());
            if (!matcher.lookingAt() && matcher.hitEnd()) {
                return start;
            }
        }
        return text.length();
    }
}
