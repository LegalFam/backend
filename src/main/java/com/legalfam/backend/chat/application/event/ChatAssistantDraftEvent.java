package com.legalfam.backend.chat.application.event;

import java.util.UUID;

/**
 * Borrador en vivo de la respuesta del asistente. No se persiste: el mensaje definitivo
 * llega despues como {@link ChatAssistantMessageEvent} y reemplaza al borrador.
 *
 * @param text     texto completo escrito hasta ahora (no un delta)
 * @param sequence orden de emision dentro del turno, para descartar borradores atrasados
 */
public record ChatAssistantDraftEvent(
        UUID sessionId,
        UUID userMessageId,
        String text,
        long sequence
) {
}
