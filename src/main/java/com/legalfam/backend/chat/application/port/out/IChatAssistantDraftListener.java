package com.legalfam.backend.chat.application.port.out;

/**
 * Recibe la respuesta del asistente mientras el flujo la escribe.
 *
 * <p>Cada llamada trae el texto completo visible hasta ese momento, no solo lo nuevo: si un
 * aviso se pierde, el siguiente lo repara. Un texto mas corto que el anterior significa que
 * el flujo reintento la redaccion y lo previo se descarta.
 */
@FunctionalInterface
public interface IChatAssistantDraftListener {

    IChatAssistantDraftListener NONE = text -> {
    };

    void onDraft(String textSoFar);
}
