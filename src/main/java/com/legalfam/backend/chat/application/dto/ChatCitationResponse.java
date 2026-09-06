package com.legalfam.backend.chat.application.dto;

public record ChatCitationResponse(
        String sourceTitle,
        // Resumen que redacta el agente XAI: es lo que lee el usuario.
        String sourceSnippet,
        // Pasaje literal del documento en el que se apoyo ese resumen, y del que sale la
        // ubicacion de la cita. Se muestra junto al resumen para que el usuario pueda
        // contrastar uno contra otro.
        String sourceOriginalSnippet,
        String sourceUrl,
        String sourceLocator,
        String sourceBreadcrumb,
        String sourceLocatorKind
) {
}
