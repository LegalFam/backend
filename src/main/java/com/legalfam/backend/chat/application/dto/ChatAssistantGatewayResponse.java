package com.legalfam.backend.chat.application.dto;

import java.util.List;

public record ChatAssistantGatewayResponse(
        // Respuesta en espanol. Es siempre la version canonica y la que se persiste como
        // `content`, aunque el usuario lea en otra lengua.
        String message,
        // La misma respuesta en la lengua del usuario. Null en conversaciones en espanol.
        String messageLocalized,
        // Traduccion al espanol de lo que escribio el usuario. El mensaje se guarda antes de
        // llamar al flujo, asi que esta es la unica oportunidad de dejar el historial en
        // espanol; sin ella los agentes recibirian turnos en idiomas mezclados.
        String userMessageTranslated,
        // El idioma que el flujo leyo del texto del usuario, que manda sobre el que se pidio
        // al enviarlo. Vacio si el flujo desplegado es anterior a la deteccion: en ese caso
        // se respeta el solicitado y todo se comporta como antes.
        String languageDetected,
        List<ChatCitationResponse> citations,
        ChatAssistantMetadata metadata
) {
    public ChatAssistantGatewayResponse {
        citations = citations == null ? List.of() : List.copyOf(citations);
        metadata = metadata == null ? ChatAssistantMetadata.empty() : metadata;
    }
}
