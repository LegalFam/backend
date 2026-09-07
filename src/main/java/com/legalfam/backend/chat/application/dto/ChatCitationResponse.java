package com.legalfam.backend.chat.application.dto;

public record ChatCitationResponse(
        String sourceTitle,
        // Resumen que redacta el agente XAI, en espanol: es la version canonica.
        String sourceSnippet,
        // El mismo resumen en la lengua del usuario. Null cuando la conversacion es en
        // espanol o cuando la traduccion no llego.
        String sourceSnippetLocalized,
        // Pasaje literal del documento en el que se apoyo ese resumen, y del que sale la
        // ubicacion de la cita. Se muestra junto al resumen para que el usuario pueda
        // contrastar uno contra otro. Nunca se traduce: es el texto de la norma.
        String sourceOriginalSnippet,
        String sourceUrl,
        String sourceLocator,
        String sourceBreadcrumb,
        String sourceLocatorKind
) {
}
